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
package genesis.learning;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;

import genesis.Config;
import genesis.corpus.CodeCorpusDB;
import genesis.corpus.CorpusPatch;
import genesis.corpus.CorpusUtils;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.transform.CodeTransAbstractor;
import genesis.transform.CodeTransAdapter;
import genesis.transform.CodeTransform;
import genesis.utils.Pair;

public class MainDebug {
	
	public static void main(String args[]) {
		if (args.length < 5) {
			System.out.println("Wrong arguments!");
			System.exit(1);
		}
		int corpusMode = Integer.parseInt(args[0]);
		String dataPath = args[1];
		int totFetch = Integer.parseInt(args[2]);
		
		CodeCorpusDB corpus = new CodeCorpusDB();
		corpus.shuffleInitFetch(corpusMode);
		ArrayList<CorpusPatch> patchInfo = new ArrayList<CorpusPatch>(corpus.fetch(totFetch));
		ArrayList<Pair<MyCtNode, MyCtNode>> codePairs = new ArrayList<Pair<MyCtNode, MyCtNode>>();
		ArrayList<ArrayList<DecomposedCodePair>> trainDB =
				new ArrayList<ArrayList<DecomposedCodePair>>();
		
		String t = args[3];
		int mode = 0;
		int coverIdx = -1;
		int n = 0;
		ArrayList<Integer> idxs = new ArrayList<Integer>();
		ArrayList<Integer> decomposedIdxs = null;
		if (t.equals("gen")) {
			mode = 0;
			n = args.length - 4;
			for (int i = 0; i < n; i++) {
				int idx = Integer.parseInt(args[i + 4]);
				idxs.add(idx);
				codePairs.add(new Pair<MyCtNode, MyCtNode>(CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).prePath), 
						CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).postPath)));
				
			}
		}
		else if (t.equals("cost")) {
			mode = 2;
			int idx = Integer.parseInt(args[4]);
			codePairs.add(new Pair<MyCtNode, MyCtNode>(CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).prePath), 
					CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).postPath)));
		}
		else {
			mode = 1;
			coverIdx = Integer.parseInt(args[4]);
			n = (args.length - 5) / 2;
			decomposedIdxs = new ArrayList<Integer>();
			for (int i = 0; i < n; i++) {
				int idx = Integer.parseInt(args[i * 2 + 5]);
				idxs.add(idx);
				decomposedIdxs.add(Integer.parseInt(args[i * 2 + 1 + 5]));
				codePairs.add(new Pair<MyCtNode, MyCtNode>(CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).prePath), 
						CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(idx).postPath)));
			}
			codePairs.add(new Pair<MyCtNode, MyCtNode>(CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(coverIdx).prePath), 
					CorpusUtils.parseJavaAST(dataPath + "/" + patchInfo.get(coverIdx).postPath)));
			coverIdx = codePairs.size() - 1;
		}
		for (int i = 0; i < codePairs.size(); i++) {
			CodePairDecomposer decomposer = new CodePairDecomposer(codePairs.get(i).x, codePairs.get(i).y);
			ArrayList<DecomposedCodePair> tmp = decomposer.decompose();
			trainDB.add(tmp);
		}
		
		if (mode == 0) {
			ArrayList<Integer> tmpList = new ArrayList<Integer>();
			enumerateGeneration(0, tmpList, trainDB, idxs.size());
		}
		else if (mode == 1) {
			ArrayList<CodeTransform> gens = generate(trainDB, decomposedIdxs);
			if (gens == null) {
				System.out.println("Abstraction failed!");
				System.exit(0);
			}
			for (CodeTransform gen : gens) {
				System.out.println("Generator:\n" + gen.toString());
				int bestCost = -1;
				ArrayList<DecomposedCodePair> tmp = trainDB.get(coverIdx);
				for (int j = 0; j < tmp.size(); j++) {
					DecomposedCodePair p = tmp.get(j);
					System.out.println("Check cover: " + j);
					System.out.println(p.toString());
					CodeTransAdapter adapter = new CodeTransAdapter(gen, p.before.getFactory());
					if (adapter.checkInside(p.insides)) {
						System.out.println("Pass insides!");
						if (adapter.applyTo(p.before)) {
							System.out.println("Can apply to!");
							if (adapter.covers(p.after)) {
								System.out.println("Cover check succ!");
								int cost = (int)adapter.estimateCost();
								System.out.println("Cost: " + cost);
								if (cost > 0) {
									if (bestCost == -1 || cost < bestCost) {
										bestCost = cost;
									}
								}
							}
						}
					}
				}
				System.out.println("Bestcost: " + bestCost);
			}
		}
		else if (mode == 2) {
			String genf = args[5];
			CodeTransform gen = null;
			try {
				FileInputStream fis = new FileInputStream(genf);
				ObjectInputStream ois =  new ObjectInputStream(fis);
				gen = (CodeTransform) ois.readObject();
				ois.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			
			System.out.println(gen.toString());
			//System.out.println("Compute for:");
			//System.out.println(pairs.toString());
			ArrayList<DecomposedCodePair> pairs = trainDB.get(0);
			ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode> > candidateBefores = Main.obtainCandidateBefores(pairs);
			System.out.println("Total candidate befores: " + candidateBefores.size());
			long sum = 0;
			for (int i = 0; i < candidateBefores.size(); i++) {
				Pair<HashSet<MyNodeSig>, MyCtNode> p = candidateBefores.get(i);
				System.out.println("Processing: " + i);
				System.out.println(p.x);
				System.out.println(p.y);
				CodeTransAdapter adapter = new CodeTransAdapter(gen, p.y.getFactory());
				if (adapter.checkInside(p.x)) {
					System.out.println("Pass inside check!");
					if (adapter.applyTo(p.y)) {
						System.out.println("Apply succ!");
						long v = adapter.estimateCost();
						System.out.println("Estimate cost: " + v);
						if (v < 0) {
							sum = Config.costlimit;
							break;
						}
						else {
							long realV = adapter.prepareGenerate();
							System.out.println("Real cost: " + realV);
							long passingV = 0;
							for (long j = 0; j < realV; j++) {
								adapter.generateOne();
								if (adapter.passTypecheck())
									passingV ++;
							}
							System.out.println("Passing cost: " + passingV);
							//sum += realV;
							sum += passingV;
							if (sum > Config.costlimit) {
								sum = Config.costlimit;
								break;
							}
						}
					}
				}
			}
			System.out.println("Task final cost: " + sum);
		}
		
	}

	private static void enumerateGeneration(int k, ArrayList<Integer> l,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, 
			int n) {
		if (k == n) {
			ArrayList<CodeTransform> gens = generate(trainDB, l);
			if (gens != null) {
				System.out.println("For: " + l.toString());
				int cnt = 0;
				for (CodeTransform gen : gens) {
					cnt ++;
					System.out.println("Generator: " + cnt);
					System.out.println(gen.toString());
					for (int i = 0; i < trainDB.size(); i++) {
						DecomposedCodePair p = trainDB.get(i).get(l.get(i));
						CodeTransAdapter adapter = new CodeTransAdapter(gen, p.before.getFactory());
						adapter.applyTo(p.before);
						long v = adapter.estimateCost();
						System.out.println("Esimate cost for " + i + ": " + v);
					}
				}
			}
			return;
		}
		int m = trainDB.get(k).size();
		for (int i = 0; i < m; i++) {
			l.add(i);
			enumerateGeneration(k + 1, l, trainDB, n);
			l.remove(l.size() - 1);
		}
	}

	static int genCnt = 0;
	
	private static ArrayList<CodeTransform> generate(ArrayList<ArrayList<DecomposedCodePair>> trainDB,
			ArrayList<Integer> l) {
		CodeTransAbstractor abst = new CodeTransAbstractor();
		genCnt ++;
		System.out.println("generate(): " + genCnt);
		for (int i = 0; i < l.size(); i++) {
			DecomposedCodePair p = trainDB.get(i).get(l.get(i));
			System.out.println(p.toString());
			abst.addMapping(p.insides, p.before, p.after);
		}
		boolean succ = abst.generalize();
		if (!succ) {
			System.out.println("Failed");
			return null;
		}
		return abst.getGenerators();
	}
}
