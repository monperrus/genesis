// Copyright (C) 2016 Fan Long, Peter Amidon, Martin Rianrd and MIT CSAIL 
// Genesis (A successor of Prophet for Java Programs)
// 
// This file is part of Genesis.
// 
// Genesis is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 2 of the License, or
// (at your option) any later version.
// 
// Genesis is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with Genesis.  If not, see <http://www.gnu.org/licenses/>.
package genesis.repair.localization;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;

import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtStatement;

import genesis.Config;
import genesis.GenesisException;
import genesis.infrastructure.ExecShellCmd;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.infrastructure.AppManager;
import genesis.repair.WorkdirManager;
import genesis.repair.ASTNodeFetcher;
import genesis.repair.validation.Testcase;
import genesis.repair.validation.TestResult;
import genesis.repair.validation.TestingOracle;
import genesis.repair.validation.ValidationOracle;
import genesis.repair.validation.TestcaseExecutor;

public class StackTraceDefectLocalization implements DefectLocalization {

	public enum DefectType {
		NPE, OOB
	}

	WorkdirManager manager;
	DefectType dt;

	public StackTraceDefectLocalization(WorkdirManager manager, DefectType dt) {
		this.manager = manager;
		this.dt = dt;
	}

	@Override
	public List<SuspiciousLocation> getSuspiciousLocations() {
		ArrayList<SuspiciousLocation> ret = new ArrayList<>();

		String workSrcDir = manager.getWorkSrcDir();
		int nLocs = Config.stackTraceLocalization_nLocs;
		int nLines = Config.stackTraceLocalization_nSurroundingLines;
		double lineWeight = Config.stackTraceLocalization_lineDistanceWeight;
		double max = nLocs + nLines * lineWeight;

		/* Use 'sed' remove try-catches in the file for the failing
		 * testcases, so that we can get stacktraces even when they
		 * would usually be caught.
		 *
		 * It would be ideal if we could construcrt a Class object,
		 * since then we could, look for the enclosing class (if this
		 * is an inner class) to hopefully get the class that the file
		 * is named after.  This probably won't work (since it is
		 * extremely unlikely that that class is on the classpath); it
		 * would be possible to do this from within a subprocess
		 * started with the correct classpath, but the test class
		 * appears to rarely be an inner class, so (for now at least)
		 * just use the provided class name as a filepath + .java
		 */
		AppManager app = manager.getApp();
		String[] testFileNames =
			manager.getNegativeCases()
			       .stream()
			       .map(e -> app.guessTestFile(e.testClass
			                                    .replaceAll("\\.", "/")
			                                    + ".java"))
                   .toArray(String[]::new);
		Path tempDirPath = null;
		try {
			tempDirPath = Files.createTempDirectory(Paths.get(Config.tmpDirectory), Config.tmpDirPrefix);
			for (String testFile : testFileNames) {
				ExecShellCmd c;
				if (Config.stackTraceLocalization_removeTry)
					c = new ExecShellCmd(new String[]{"sed", "s/try/if(true)/g;s/catch\\([^(]*\\)$/if(false)\\1/g;s/catch\\([^(]\\)(\\([^)]*\\))\\([^(]*\\)/if(false)\\1\\3\\2=null;/g;s/finall/if(false)/g", testFile}, workSrcDir, true, false);
				else
					c = new ExecShellCmd(new String[]{"cat", testFile}, workSrcDir, true, false);
				c.waitExit();
				String sourcePath = Paths.get(workSrcDir)
				                         .toAbsolutePath()
				                         .relativize(Paths.get(testFile))
				                         .toString();
				manager.compileJavaTestTo(sourcePath, c.getOutput(), tempDirPath);
			}
		} catch (IOException | InterruptedException e) {
			// Something went wrong somewhere in there.  Maybe the old
			// version was okay; just ignore it for now.
		}

		/* Declare this as a TestingOracle instead of a
		 * ValidatonOracle in order to get access to
		 * runTestcasesForResults(Path, List<Testcase>)
		 */
		TestingOracle oracle = new TestingOracle(manager);
		Map<Testcase, TestResult> res =
			oracle.runTestcasesForResults(tempDirPath, manager.getNegativeCases());

		if (tempDirPath != null) {
			try {
				FileUtils.deleteDirectory(tempDirPath.toFile());
			} catch (IOException e) {
				// No idea what might have gone wrong here. Since the
				// whole testcase rewriting is being considered
				// non-essential, let's hope that it's alright.
			}
		}

		// Use everything that looks vaguely like a stack trace line
		// Basically, anything that looks like 'at [^
		// ]*(xxx.java:yyy)'
		Pattern elePattern = Pattern.compile("at (.*)\\.[^.]*\\.[^.(]*\\((.+\\.java):([0-9]+)\\)");
		ASTNodeFetcher fetcher = new ASTNodeFetcher(manager);
		for (Entry<Testcase, TestResult> e : res.entrySet()) {
			// We have several try catch to wrap exception as GenesisException to upper level
			try {
				int i = 0;
				boolean isNPE = false;
				boolean lookingForMessage = false;
				boolean lookingForLines = false;
				for (String l : e.getValue().msg.split("\n")) {
					try {
						// Use everything that looks vaguely like a stack
						// trace line Basically, anything that looks like 'at
						// [^ ]*(xxx.java:yyy)'
						Matcher matcher = elePattern.matcher(l);
						while (matcher.find()) {
							try {
								String absPath = app.guessSrcFile(matcher.group(1),
																  matcher.group(2));
								if (absPath == null) {
									// The file is not from this project.  Ignore it.
									continue;
								}
								String relPath = Paths.get(workSrcDir)
									.toAbsolutePath()
									.relativize(Paths.get(absPath))
									.toString();
								int ln = Integer.parseInt(matcher.group(3));
								for (int j = -nLines; j <= nLines; j++) {
									double sp = 0.5 - (i + lineWeight * Math.abs(j)) / max;
									// Before is more likely to be the problem than after
									if (j > 0) sp -= 0.2;
									SuspiciousLocation loc = new SuspiciousLocation(relPath, ln+j, -1, sp);
									HashMap<MyCtNode, HashSet<MyNodeSig>> fetchResult = fetcher.fetch(loc);
									if (fetchResult == null) {
										// No statements here; let's
										// not even bother adding it.
										continue;
									}
									List<CtElement> ifSubNodes = new ArrayList<>();
									List<CtElement> trySubNodes = new ArrayList<>();
									List<CtElement> forSubNodes = new ArrayList<>();
									List<CtElement> whileSubNodes = new ArrayList<>();
									for (MyCtNode n : fetchResult.keySet()) {
										CtStatement s = (CtStatement)n.getRawObject();
										ifSubNodes.addAll(s.getElements(new TypeFilter<CtIf>(CtIf.class)));
										trySubNodes.addAll(s.getElements(new TypeFilter<CtTry>(CtTry.class)));
										forSubNodes.addAll(s.getElements(new TypeFilter<CtFor>(CtFor.class)));
										whileSubNodes.addAll(s.getElements(new TypeFilter<CtWhile>(CtWhile.class)));
									}
									if (ifSubNodes.size() > 0 ||
										trySubNodes.size() > 0 ||
										forSubNodes.size() > 0 ||
										whileSubNodes.size() > 0) {
										sp += 0.5;
										loc = new SuspiciousLocation(relPath, ln+j, -1, sp);
									}
									int idx = ret.indexOf(loc);
									if (idx != -1) {
										// It already exists.  Since
										// SuspiciousLocations are
										// immutable, we'll replace it
										// if this one is more
										// suspicious.
										if (sp > ret.get(idx).getSuspiciousness()) {
											ret.set(idx, loc);
										}
									} else {
										ret.add(loc);
									}
								}
							} catch (Exception err) {
								throw new GenesisException("Localization failed with the cause: " + err.getMessage(), err);
							}
						}
					} catch (Exception err) {
						throw new GenesisException("Localization failed with the cause: " + err.getMessage(), err);
					}
				}
			} catch (Exception err) {
				throw new GenesisException("Localization failed with the cause: " + err.getMessage(), err);
			}
		}
		Comparator<SuspiciousLocation> comp =
			Comparator.comparing(SuspiciousLocation::getSuspiciousness)
			          .reversed();
		Collections.sort(ret, comp);
		return ret;
	}
}
