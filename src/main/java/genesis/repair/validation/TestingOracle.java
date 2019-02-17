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
package genesis.repair.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

import genesis.Config;
import genesis.GenesisException;
import genesis.repair.WorkdirManager;

public class TestingOracle implements ValidationOracle {

	public final static int FailedPosCaseBatch = 3;

	WorkdirManager manager;
	HashSet<Testcase> failedPosCases;
	
	public TestingOracle(WorkdirManager manager) {
		this.manager = manager;
		this.failedPosCases = new HashSet<Testcase>();
	}

	@Override
	public ValidationResult validate(String sourcePath, String newCodeStr, boolean verbose) {
		Path tempDirPath = null;
		try {
			tempDirPath = Files.createTempDirectory(Paths.get(Config.tmpDirectory), Config.tmpDirPrefix);
			boolean succ = manager.compileJavaSourceTo(sourcePath, newCodeStr, tempDirPath);
			// Compilation error, going to skip
			if (!succ) {
				//System.out.println("Compilation failed!");
				FileUtils.deleteDirectory(tempDirPath.toFile());
				return ValidationResult.COMPILE_FAIL;
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println(newCodeStr);
			System.out.println("IOException when validating a patch: " + sourcePath);
			return ValidationResult.COMPILE_FAIL;
		}
		
		List<Testcase> ret = runTestcases(tempDirPath, manager.getNegativeCases());
		if (ret.size() != manager.getNegativeCases().size()) {
			if (verbose) {
				System.out.println("Failed negative cases:");
				List<Testcase> failedCases = getFailedCases(ret, manager.getNegativeCases());
				for (Testcase c : failedCases) {
					System.out.print(c.toString() + " ");
				}
				System.out.println();
			}
			return ValidationResult.FAIL;
		}
		
		if (verbose) System.out.println("Passed negative cases!");
		
		if (failedPosCases.size() != 0) {
			ArrayList<Testcase> cases = new ArrayList<Testcase>();
			for (Testcase c1 : failedPosCases) {
				cases.add(c1);
				if (cases.size() >= FailedPosCaseBatch) {
					//System.out.println("Running for: ");
					//System.out.println(cases.toString());
					//ArrayList<Testcase> cases = new ArrayList<Testcase>(failedPosCases);
					ret = runTestcases(tempDirPath, cases);
					if (ret.size() != cases.size()) {
						if (verbose) {
							List<Testcase> failedCases = getFailedCases(ret, cases);
							System.out.println("Failed positive cases:");
							for (Testcase c : failedCases) {
								System.out.print(c.toString() + " ");
							}
							System.out.println();
						}
						return ValidationResult.FAIL;
					}
					cases.clear();
				}
			}
			
			if (cases.size() > 0) {
				//System.out.println("Running for: ");
				//System.out.println(cases.toString());
				ret = runTestcases(tempDirPath, cases);
				if (ret.size() != cases.size()) {
					if (verbose) {
						List<Testcase> failedCases = getFailedCases(ret, cases);
						System.out.println("Failed positive cases:");
						for (Testcase c : failedCases) {
							System.out.print(c.toString() + " ");
						}
						System.out.println();
					}
					return ValidationResult.FAIL;
				}
			}
		}
		
		HashSet<Testcase> remainingCases = new HashSet<Testcase>(manager.getPositiveCases());
		remainingCases.removeAll(failedPosCases);
		ArrayList<Testcase> cases = new ArrayList<Testcase>(remainingCases);
		ret = runTestcases(tempDirPath, cases);
		if (ret.size() != cases.size()) {
			List<Testcase> failedCases = getFailedCases(ret, cases);
			if (verbose) {
				System.out.println("Failed positive cases:");
				for (Testcase c : failedCases) {
					System.out.print(c.toString() + " ");
				}
				System.out.println();
			}
			failedPosCases.addAll(failedCases);
			return ValidationResult.FAIL;
		}
		
		System.out.println("Pass positive cases!");
		
		try {
			FileUtils.deleteDirectory(tempDirPath.toFile());
		}
		catch (IOException ignore) { }
		
		return ValidationResult.PASS;
	}

	private List<Testcase> getFailedCases(List<Testcase> passed, Collection<Testcase> a) {
		ArrayList<Testcase> ret = new ArrayList<Testcase>();
		HashSet<Testcase> tmp = new HashSet<Testcase>(passed);
		for (Testcase c : a) {
			if (!tmp.contains(c))
				ret.add(c);
		}
		return ret;
	}
	
	public Map<Testcase, TestResult> runTestcasesForResults(Path extraTestClassPath, List<Testcase> cases) {
		HashMap<Integer, ArrayList<Testcase>> sessionM = new HashMap<Integer, ArrayList<Testcase>>();
		for (Testcase c : cases) {
			int id = manager.getTestSessionId(c.testClass);
			if (id < 0) {
				System.out.println("[WARN]Unable to get test session id for test case: " + c);
				System.out.println("[WARN]Count this testcase failed!");
				continue;
			}
			if (!sessionM.containsKey(id))
				sessionM.put(id, new ArrayList<Testcase>());
			sessionM.get(id).add(c);
		}
		
		HashMap<Testcase, TestResult> ret = new HashMap<>();
		for (Integer id : sessionM.keySet()) {
			String testClassPath = manager.getTestSessionClasspath(id);
			if (extraTestClassPath != null)
				testClassPath = extraTestClassPath.toString() + Config.classPathSep + testClassPath;
			//System.out.println("Testing classpath: " + testClassPath);
			TestcaseExecutor exec = new TestcaseExecutor(testClassPath, sessionM.get(id), manager.getWorkSrcDir());
			exec.run();
			HashMap<Testcase, TestResult> res = null;
			try {
				if (Config.perCaseTimeout == 0)
					res = exec.getResult();
				else
					res = exec.getResult(Config.perCaseTimeout * sessionM.get(id).size());
			}
			catch (InterruptedException e) {
				e.printStackTrace();
				throw new GenesisException("Genesis is interrupted during testing!");
			}
			ret.putAll(res);
		}
		return ret;
	}

	public Map<Testcase, TestResult> runTestcasesForResults(List<Testcase> cases)  {
		return runTestcasesForResults(null, cases);
	}

	public List<Testcase> runTestcases(Path extraTestClassPath, List<Testcase> cases) {
		Map<Testcase, TestResult> res =
			runTestcasesForResults(extraTestClassPath, cases);
		ArrayList<Testcase> ret = new ArrayList<>();
		for (Entry<Testcase, TestResult> e : res.entrySet()) {
			if (e.getValue().getPass()) {
				//System.out.println("Passed: " + e.getKey());
				ret.add(e.getKey());
			}
			else {
				//System.out.println("Failed: " + e.getKey());
			}
		}
		return ret;
	}

	@Override
	public List<Testcase> runTestcases(List<Testcase> cases) {
		return runTestcases(null, cases);
	}

}
