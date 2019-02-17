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
package genesis.repair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.RandomUtils;

import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.repair.localization.StackTraceDefectLocalization;
import genesis.repair.localization.DefectLocalization;
import genesis.repair.localization.SuspiciousLocation;
import genesis.repair.validation.TestingOracle;
import genesis.repair.validation.ValidationOracle;
import genesis.repair.validation.ValidationOracle.ValidationResult;
import genesis.rewrite.CodeRewriter;
import genesis.space.SearchSpace;
import genesis.utils.Pair;
import genesis.GenesisException;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class Main {
	
	private static String getTmpDirectory() {
		String ret = null;
		do {
			int idx = RandomUtils.nextInt(0, 10000);
			ret = "_tmpwdir" + idx;
		}
		while (Files.exists(Paths.get(ret)));
		return ret;
	}
	
	public static void printErr(Exception e) {
		System.out.println("!! Patch generation failed with exception:");
		e.printStackTrace(System.out);
		System.out.println("!! Going to skip this patch and continue!");
	}
	
	public static void main(String args[]) {
		System.out.println("Initial timestamp: " + new Date());
		Options opts = new Options();
		opts.addOption(Option.builder("w").longOpt("workdir").hasArg().desc("specify the work directory name to create or use").build());
		opts.addOption(Option.builder("par").longOpt("par").desc("Use PAR search space, this will override other search space configuration.").build());
		opts.addOption(Option.builder("parnpe").longOpt("parnpe").desc("Use NPE templates in PAR search space, this will override other search space configuration.").build());
		opts.addOption(Option.builder("paroob").longOpt("paroob").desc("Use OOB templates in PAR search space, this will override other search space configuration.").build());
		opts.addOption(Option.builder("parcce").longOpt("parcce").desc("Use CCE templates in PAR search space, this will override other search space configuration.").build());
		opts.addOption(Option.builder("s").longOpt("search-space").hasArgs().valueSeparator(',').desc("the search space file, default space.txt").build());
		opts.addOption(Option.builder("c").longOpt("candidate-dir").hasArgs().valueSeparator(',').desc("the directory for pattern candidate, default ./candidate").build());
		opts.addOption(Option.builder("io").longOpt("init-only").desc("Only initialize the workdir then just exit!").build());
		opts.addOption(Option.builder("si").longOpt("skip-init").desc("Skip initialization and reuse as much existing information in workdir as possible, " + 
						"this assumes the existing workdir is not malformed!").build());
		opts.addOption(Option.builder("sl").longOpt("skip-localization").desc("Skip localization also, only works with init-only").build());
		opts.addOption(Option.builder("lo").longOpt("localize-only").desc("Only try to localize, printing the localization results").build());
		opts.addOption(Option.builder("cp").longOpt("count-patches").desc("Do not try to validate patches, only printing the total number generated").build());
        opts.addOption(Option.builder("oob").longOpt("oob-case").desc("Run for OOB cases; at present only changes localization to check for out of "
        		+ "bounds instead of null pointer exceptions.").build());
        opts.addOption(Option.builder("fo").longOpt("failure-oblivious").desc("Running in failure oblivious mode to catch, print, "
        		+ "and ignore most exceptions during repairs.").build());
		
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(opts, args);
		}
		catch (ParseException e) {
			e.printStackTrace();
			System.out.println("Unable to parse the command line exception! Use --help!");
			System.exit(1);
		}
		
		List<String> argList = line.getArgList();
		if (argList.isEmpty() && !line.hasOption("w") ) {
			System.out.println("Has to specify a configuration file or an existing work directory!");
			System.exit(1);
		}
		
		System.out.println("Initialize workdir...");
		WorkdirManager manager = null;
		if (argList.isEmpty()) {
			String workDir = line.getOptionValue("w");
			manager = WorkdirManager.createWithExistingWorkdir(workDir, line.hasOption("skip-init"));
		}
		else {
			String workDir = null;
			if (line.hasOption("w"))
				workDir = line.getOptionValue("w");
			else
				workDir = getTmpDirectory();
			manager = WorkdirManager.createWithConfigFile(argList.get(0), workDir);
		}
		System.out.println("Initialization complete!");
		
		List<SuspiciousLocation> locs = null;
		// Defect localization process 
		if (manager.hasSuspiciousLocations() && !line.hasOption("localize-only"))
			locs = manager.getSuspiciousLocations();
		else {
			if (!line.hasOption("init-only") || !line.hasOption("skip-localization")) {
				System.out.println("Running defect localization...");
				StackTraceDefectLocalization.DefectType dt;
                if (line.hasOption("oob")) {
					dt = StackTraceDefectLocalization.DefectType.OOB;
				} else {
					dt = StackTraceDefectLocalization.DefectType.NPE;
				}
                try {
                	DefectLocalization localizer = new StackTraceDefectLocalization(manager, dt);
                	locs = localizer.getSuspiciousLocations();
                	manager.setSuspiciousLocations(locs);
                }
                catch (GenesisException err) {
                	if (line.hasOption("fo")) {
                		System.out.println("!! Localization failed with exception:");
    					err.printStackTrace(System.out);
    					if (err.getCause() != null)
    						err.getCause().printStackTrace(System.out);
                	}
                	else
                		throw err;
                }
				System.out.println("Defect localization complete!");
			}
		}

		if (line.hasOption("localize-only")) {
			System.out.println(locs);
			System.out.println("Final timestamp: " + new Date());
			return;
		}
		
		if (line.hasOption("init-only")) {
			System.out.println("Init-only mode. Initialization stage ends, exit gracefully.");
			System.out.println("Final timestamp: " + new Date());
			return;
		}
		
		SearchSpace space = null;
		
		if (line.hasOption("par") || line.hasOption("parnpe") || line.hasOption("paroob") || line.hasOption("parcce")) {
			if (line.hasOption("par")) {
				space = SearchSpace.createPARSearchSpace();
				System.out.println("Use the PAR search space.");
			}
			else if (line.hasOption("parnpe")) {
				space = SearchSpace.createPARNPESearchSpace();
				System.out.println("Use the PARNPE search space.");
			}
			else if (line.hasOption("paroob")) {
				space = SearchSpace.createPAROOBSearchSpace();
				System.out.println("Use the PAROOB search space.");
			}
			else if (line.hasOption("parcce")) {
				space = SearchSpace.createPARCCESearchSpace();
				System.out.println("Use the PARCCE search space.");
			}
		}
		else {
			String[] spaceFiles = {"space.txt"};
			String[] candidateDirs = {"./candidate"};
			if (line.hasOption("s")) {
				spaceFiles = line.getOptionValues("s");
			}
			if (line.hasOption("c")) {
				candidateDirs = line.getOptionValues("c");
			}
			if (spaceFiles.length != candidateDirs.length) {
				System.err.println("The number of space files and the number of candidate directories do not match!");
				System.exit(1);
			}
			System.out.println("Use the search space from: " + Arrays.toString(spaceFiles) + " and " + Arrays.toString(candidateDirs));
			space = new SearchSpace(spaceFiles, candidateDirs);
		}
		
		ASTNodeFetcher fetcher = new ASTNodeFetcher(manager);
		int patchCnt = 0;
		int candidateCnt = 0;
		int compileFailCnt = 0;
		int duplicateCnt = 0;
		ValidationOracle oracle = new TestingOracle(manager);
		HashMap<MyCtNode, HashSet<String>> tested = new HashMap<MyCtNode, HashSet<String>>();
        HashSet<String> validatedPatches = new HashSet<>();
		for (SuspiciousLocation loc : locs) {
			HashMap<MyCtNode, HashSet<MyNodeSig>> fetchResult;
			// All these trys are hacks to not die when various
			// exceptions get thrown by the rewriter, typechecker, etc,
			// during failure oblivious mode. This mode is helpful
			// if we want to force the system to continue to get
			// some results even with error.
			try {
				fetchResult = fetcher.fetch(loc);
				/* Some localization strategies may provide lines that do
				   not correspond to statements.
				*/
				if (fetchResult == null) { continue; }
			} catch (Exception err) {
				if (line.hasOption("fo"))
					printErr(err);
				else
					throw err;
				continue;
			}
			for (Map.Entry<MyCtNode, HashSet<MyNodeSig>> e : fetchResult.entrySet()) {
				MyCtNode root;
				ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> candidatePairs;
				try {
					MyCtNode node = e.getKey();
					HashSet<MyNodeSig> insides = e.getValue();
					root = fetcher.fetchRoot(loc);
					//System.out.println("Node:\n" + node);
					candidatePairs = ASTNodeCollector.getCandidateNodes(insides, node);
					//System.out.println("Size: " + candidatePairs.size());
				} catch (Exception err) {
					if (line.hasOption("fo"))
						printErr(err);
					else
						throw err;
					continue;
				}
				for (Pair<HashSet<MyNodeSig>, MyCtNode> p : candidatePairs) {
					//System.out.println(p.y);
					List<SearchSpace.GenerationResult> candidatePatches;
					try {
						candidatePatches = space.applyTo(p.x, p.y);
					} catch (Exception err) {
						if (line.hasOption("fo"))
							printErr(err);
						else
							throw err;
						continue;
					}
					for (SearchSpace.GenerationResult res : candidatePatches) {
						MyCtNode candidatePatch = res.patch;
						try {
							candidateCnt ++;
							System.out.println("=====================================");
							System.out.println("Patch timestamp: " + new Date());
							System.out.println("Candidte patch: " + candidateCnt);
							System.out.println("Localization: " + loc);
							// XXX: Just combine the two index together in case of multiple search space
							System.out.println("Generator index: " + (res.sidx * 10000 + res.gidx) ); 
							CodeRewriter rewriter = new CodeRewriter(root);
							String newCodeStr;
							rewriter.setCommentString("genesis generated change");
							rewriter.addMapping(p.y, candidatePatch);
							newCodeStr = rewriter.rewrite();
							System.out.println("Validating:");
							String patchSnippet = candidatePatch.codeString(p.y);
							System.out.println(patchSnippet);
							System.out.println("Replacing:");
							System.out.println(p.y.codeString());					
							if (tested.containsKey(p.y)) {
								HashSet<String> tmp = tested.get(p.y);
								if (tmp.contains(patchSnippet)) {
									System.out.println("Duplicate tested patch, ignored!");
									duplicateCnt ++;
									continue;
								}
								else
									tmp.add(patchSnippet);
							}
							else {
								HashSet<String> tmp = new HashSet<String>();
								tmp.add(patchSnippet);
								tested.put(p.y, tmp);
							}
                            if (validatedPatches.contains(newCodeStr)) {
								System.out.println("Duplicate validated patch, ignored!");
								duplicateCnt++;
								continue;
                            }
							if (line.hasOption("count-patches")) {
								continue;
							}
							ValidationResult vres = oracle.validate(loc.getSourcePath(), newCodeStr, true); 
							if (vres == ValidationResult.PASS) {
								validatedPatches.add(newCodeStr);
								patchCnt ++;
								System.out.println("We found a patch, total patch cnt: " + patchCnt);
								dumpPatchToFile(newCodeStr, loc.getSourcePath(), "__patch" + patchCnt + ".java");
							}
							else if (vres == ValidationResult.COMPILE_FAIL) {
								compileFailCnt ++;
							}
						} catch (Exception err) {
							if (line.hasOption("fo"))
								printErr(err);
							else
								throw err;
							continue;
						}
					}
				}
			}
		}
		if (line.hasOption("count-patches")) {
		  System.out.println("Total number of candidate patches: " + candidateCnt);
		  System.out.println("Total number of duplicate patches: " + duplicateCnt);
		  System.out.println("Total number of candidate failing malform checks: " + space.getFailedCheckCnt());
		} else {
		  System.out.println("Explored the whole space, we got: " + patchCnt);
		  System.out.println("Total number of candidate patches: " + candidateCnt);
		  System.out.println("Total number of compilation failures: " + compileFailCnt);
		  System.out.println("Total number of duplicate patches: " + duplicateCnt);
		  System.out.println("Total number of candidate failing malform checks: " + space.getFailedCheckCnt());
		}
		if (!line.hasOption("w"))
			manager.destroyWorkdir();
		System.out.println("Final timestamp: " + new Date());
	}

	private static void dumpPatchToFile(String newCodeStr, String sourcePath, String fname) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(fname));
			writer.write("//" + sourcePath);
			writer.newLine();
			writer.write(newCodeStr);
			writer.close();
		}
		catch (IOException e) {
			e.getStackTrace();
			System.out.println("Unable to write the patch to the file: " + fname);
		}
	}
}
