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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import genesis.Config;
import genesis.corpus.CodeCorpusDB;
import genesis.corpus.CorpusApp;
import genesis.corpus.CorpusPatch;
import genesis.corpus.CorpusUtils;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.repair.ASTNodeCollector;
import genesis.transform.CodeTransAbstractor;
import genesis.transform.CodeTransAdapter;
import genesis.transform.CodeTransform;
import genesis.utils.Pair;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.support.reflect.code.CtCodeElementImpl;

public class Main {

	public static int ThreadCount = 4;
	public static int RoundCount = 4;
	//public static int FitnessTrainBonus = (int)(Config.costlimit / 10);
	public static int FitnessTrainRatio = 10;
	public static int FitnessValidateBonus = (int)Config.costlimit;
	public static int PopLimit = 500;
	public static int CrossoverRound = 2;
	public static boolean ComputeFullCost = true;
	public static boolean SkipRound2 = true;
	public static boolean enableCrossover = false;
	
	static class Round1Task implements Runnable {
		private int i, j;
		private ArrayList<ArrayList<DecomposedCodePair>> trainDB;
		ArrayList<Integer> res;
		
		Round1Task(ArrayList<ArrayList<DecomposedCodePair>> trainDB, 
			int i, int j) {
			this.i = i;
			this.j = j;
			this.trainDB = trainDB;
			this.res = null;
		}
		
		@Override
		public void run() {
			res = null;
			ArrayList<DecomposedCodePair> tmp1List = trainDB.get(i);
			//if (patchInfo.get(i).appId == patchInfo.get(j).appId) continue;
			ArrayList<DecomposedCodePair> tmp2List = trainDB.get(j);
			for (int a = 0; a < tmp1List.size(); a++)
				for (int b = 0; b < tmp2List.size(); b++) {
					System.out.println("current running: " + i + " " + j + " " + a + " " + b);
					DecomposedCodePair p1 = tmp1List.get(a);
					DecomposedCodePair p2 = tmp2List.get(b);
					CodeTransAbstractor abst = new CodeTransAbstractor();
					//System.out.println(p1.toString());
					//System.out.println(p2.toString());
					abst.addMapping(p1.insides, p1.before, p1.after);
					abst.addMapping(p2.insides, p2.before, p2.after);
					boolean succ = abst.generalize();
					if (succ) {
						//System.out.println("Generation Succ!");
						int numGen = abst.getNumGenerators();
						for (int x = 0; x < numGen; x++) {
							CodeTransform gen = abst.getGenerator(x);
							//System.out.println("Gen " + x + ":");
							//System.out.println(gen.toString());
							CodeTransAdapter adapter;
							int cost1 = 0, cost2 = 0;
							{
								adapter = new CodeTransAdapter(gen, p1.before.getFactory());
								adapter.applyTo(p1.before);
								cost1 = (int)adapter.estimateCost();
							}
							{
								adapter = new CodeTransAdapter(gen, p2.before.getFactory());
								adapter.applyTo(p2.before);
								cost2 = (int)adapter.estimateCost();
							}
							if (cost1 > 0 && cost2 > 0) {
								int cost = cost1 > cost2 ? cost1 : cost2;
								System.out.println(gen.toString());
								System.out.println("Cost: " + cost);
								if (res == null || res.get(0) > cost) {
									ArrayList<Integer> tmp = new ArrayList<Integer>();
									tmp.add(cost);
									tmp.add(a);
									tmp.add(b);
									res = tmp;
								}
							}
						}
					}
				}
		}
	};
	
	public static Table<Integer, Integer, ArrayList<Integer>> 
			computeRound1Res(ArrayList<ArrayList<DecomposedCodePair>> trainDB, 
			ArrayList<CorpusPatch> patchInfo, int n1) {
		ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
		ArrayList<Round1Task> tasks = new ArrayList<Round1Task>();
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>();
		for (int i = 0; i < n1; i++)
			for (int j = i + 1; j < n1; j++) {
				//if (patchInfo.get(i).appId == patchInfo.get(j).appId) continue;
				Round1Task t = new Round1Task(trainDB, i, j);
				futures.add(pool.submit(t));
				tasks.add(t);
				
			}
		pool.shutdown();
		while (!pool.isTerminated()) {
			try {
				pool.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		for (int i = 0; i < futures.size(); i++) {
			try {
				if (futures.get(i).isDone())
					futures.get(i).get();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			catch (ExecutionException e) {
				System.out.println("Execution ERROR for ROUND1 Task " + tasks.get(i).i + "-" + tasks.get(i).j + "!!!!!!!");
				System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
			}
		}
		Table<Integer, Integer, ArrayList<Integer>> res = HashBasedTable.create();
		for (Round1Task t : tasks) {
			if (t.res != null) {
				res.put(t.i, t.j, t.res);
			}
		}
		return res;
	}

	private static Table<Integer, Integer, ArrayList<Integer>> readRound1Res(File round1f) {
		Table<Integer, Integer, ArrayList<Integer>> res = HashBasedTable.create();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(round1f));
			String line;
			while ((line = reader.readLine()) != null) {
				Scanner s = new Scanner(line);
				int r = s.nextInt();
				int c = s.nextInt();
				ArrayList<Integer> tmp = new ArrayList<Integer>();
				while (s.hasNextInt()) {
					tmp.add(s.nextInt());
				}
				res.put(r, c, tmp);
				s.close();
			}
			reader.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	private static void writeRound1Res(Table<Integer, Integer, ArrayList<Integer>> res, File round1f) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(round1f));
			for (Cell<Integer, Integer, ArrayList<Integer>> c : res.cellSet()) {
				writer.write(c.getRowKey() + " " + c.getColumnKey());
				for (Integer v : c.getValue()) {
					writer.write(" " + v);
				}
				writer.newLine();
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void addToCandidateMap(HashMap<Integer, ArrayList<Integer>> candidateM,
			Table<Integer, Integer, ArrayList<Integer>> graph, Integer a, Integer b) {
		ArrayList<Integer> tmp = graph.get(a, b);
		assert ((tmp.size() % 2) == 0);
		for (int i = 0; i < tmp.size() / 2; i++) {
			int x = tmp.get(i * 2);
			int y = tmp.get(i * 2 + 1);
			if (!candidateM.containsKey(x))
				candidateM.put(x, new ArrayList<Integer>());
			candidateM.get(x).add(y);
		}
	}
	
	static class Round2Task implements Runnable {

		private Cell<Integer, Integer, ArrayList<Integer>> c;
		private ArrayList<ArrayList<DecomposedCodePair>> trainDB;
		private ArrayList<CorpusPatch> patchInfo;
		private Table<Integer, Integer, ArrayList<Integer>> graph;
		private int n1;
		ArrayList<ArrayList<Integer>> res2;
		
		Round2Task(Cell<Integer, Integer, ArrayList<Integer>> c,
				ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<CorpusPatch> patchInfo,
				Table<Integer, Integer, ArrayList<Integer>> graph,
				int n1) {
			this.c = c;
			this.n1 = n1;
			this.trainDB = trainDB;
			this.patchInfo = patchInfo;
			this.graph = graph;
			this.res2 = null;
		}
		
		@Override
		public void run() {
			res2 = new ArrayList<ArrayList<Integer>>();
			DecomposedCodePair p1 = trainDB.get(c.getRowKey()).get(c.getValue().get(1));
			DecomposedCodePair p2 = trainDB.get(c.getColumnKey()).get(c.getValue().get(2));
			HashMap<Integer, ArrayList<Integer>> candidateM = new HashMap<Integer, ArrayList<Integer>>(); 
			addToCandidateMap(candidateM, graph, c.getRowKey(), c.getValue().get(1));
			addToCandidateMap(candidateM, graph, c.getColumnKey(), c.getValue().get(2));
			
			for (Integer i : candidateM.keySet()) {
				if (patchInfo.get(i).appId == patchInfo.get(c.getRowKey()).appId) continue;
				if (patchInfo.get(i).appId == patchInfo.get(c.getColumnKey()).appId) continue;
				ArrayList<DecomposedCodePair> tmpList = trainDB.get(i);
				int bestCost = -1;
				int besta = 0;
				for (Integer a : candidateM.get(i)) {
					DecomposedCodePair p3 = tmpList.get(a);
					System.out.println("current running: " + c.getRowKey() + " " + 
							c.getValue().get(1) + " " + c.getColumnKey() + " " + c.getValue().get(2) + " " + i + " " + a);
					CodeTransAbstractor abst = new CodeTransAbstractor();
					abst.addMapping(p1.insides, p1.before, p1.after);
					abst.addMapping(p2.insides, p2.before, p2.after);
					abst.addMapping(p3.insides, p3.before, p3.after);
					boolean succ = abst.generalize();
					if (succ) {
						System.out.println("Succ!");
						int numGen = abst.getNumGenerators();
						for (int x = 0; x < numGen; x++) {
							CodeTransform gen = abst.getGenerator(x);
							CodeTransAdapter adapter;
							int cost1 = 0, cost2 = 0, cost3 = 0;
							{
								adapter = new CodeTransAdapter(gen, p1.before.getFactory());
								adapter.applyTo(p1.before);
								cost1 = (int)adapter.estimateCost();
							}
							{
								adapter = new CodeTransAdapter(gen, p2.before.getFactory());
								adapter.applyTo(p2.before);
								cost2 = (int)adapter.estimateCost();
							}
							{
								adapter = new CodeTransAdapter(gen, p3.before.getFactory());
								adapter.applyTo(p3.before);
								cost3 = (int)adapter.estimateCost();
							}
							if (cost1 > 0 && cost2 > 0 && cost3 > 0) {
								int cost = cost1 > cost2 ? cost1 : cost2;
								System.out.println(gen.toString());
								System.out.println("Cost: " + cost);
								cost = cost > cost3 ? cost : cost3;
								if (bestCost == -1 || bestCost > cost) {
									bestCost = cost;
									besta = a;
								}
							}
						}
					}
				}
				
				if (bestCost != -1) {
					ArrayList<Integer> tmp = new ArrayList<Integer>();
					tmp.add(bestCost);
					tmp.add(c.getRowKey());
					tmp.add(c.getValue().get(1));
					tmp.add(c.getColumnKey());
					tmp.add(c.getValue().get(2));
					tmp.add(i);
					tmp.add(besta);
					res2.add(tmp);
				}
			}
		}
	}
	
	public static ArrayList<ArrayList<Integer>> computeRound2Res(Table<Integer, Integer, ArrayList<Integer>> res,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<CorpusPatch> patchInfo,
			Table<Integer, Integer, ArrayList<Integer>> graph,
			int n1) {
		ArrayList<ArrayList<Integer>> res2 = new ArrayList<ArrayList<Integer>>();
		
		ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
		ArrayList<Round2Task> tasks = new ArrayList<Round2Task>();
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>();
		for (Cell<Integer, Integer, ArrayList<Integer>> c: res.cellSet()) {
			Round2Task t = new Round2Task(c, trainDB, patchInfo, graph, n1);
			futures.add(pool.submit(t));
			tasks.add(t);
		}
		pool.shutdown();
		while (!pool.isTerminated()) {
			try {
				pool.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		for (int i = 0; i < futures.size(); i++) {
			try {
				if (futures.get(i).isDone())
					futures.get(i).get();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			catch (ExecutionException e) {
				System.out.println("Execution ERROR for ROUND2 Task " + tasks.get(i).c + "!!!!!!!");
				System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
			}
		}
		for (Round2Task t : tasks) {
			res2.addAll(t.res2);
		}
		return res2;
	}
	
	private static void writeRound2Res(ArrayList<ArrayList<Integer>> res2, File round2f) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(round2f));
			for (ArrayList<Integer> a : res2) {
				for (Integer v : a) {
					writer.write(v + " ");
				}
				writer.newLine();
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static ArrayList<ArrayList<Integer>> readRound2Res(File round2f) {
		ArrayList<ArrayList<Integer>> res = new ArrayList<ArrayList<Integer>>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(round2f));
			String line = null;
			while ((line = reader.readLine()) != null) {
				Scanner s = new Scanner(line);
				ArrayList<Integer> tmp = new ArrayList<Integer>();
				while (s.hasNextInt()) {
					tmp.add(s.nextInt());
				}
				res.add(tmp);
				s.close();
			}
			reader.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
	
	static class PopEntry {
		HashMap<Integer, Integer> m;
		ArrayList<CodeTransform> gens;
		ArrayList<HashMap<Integer, Integer>> covers;
		Integer fitness;
		
		PopEntry(HashMap<Integer, Integer> m, ArrayList<CodeTransform> gens, 
				ArrayList<HashMap<Integer, Integer>> covers, int fitness) {
			this.m = m;
			this.gens = gens;
			this.covers = covers;
			this.fitness = fitness;
		}
	};
	
	static class PopEntryComparator implements Comparator<PopEntry> {

		@Override
		public int compare(PopEntry arg0, PopEntry arg1) {
			return arg1.fitness - arg0.fitness;
		}
		
	}
	
	private static boolean weakDominate(PopEntry a, PopEntry b) {
		if (a.fitness < b.fitness) return false;
		for (int i = 0; i < a.gens.size(); i++) {
			HashMap<Integer, Integer> covera = a.covers.get(i);
			boolean supCover = true;
			for (int j = 0; j < b.gens.size(); j++) {
				HashMap<Integer, Integer> coverb = b.covers.get(j);
				if (!covera.keySet().containsAll(coverb.keySet())) {
					supCover = false;
					break;
				}
			}
			if (supCover) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean strongDominate(PopEntry a, PopEntry b) {
		for (int i = 0; i < a.gens.size(); i++) {
			HashMap<Integer, Integer> covera = a.covers.get(i);
			boolean supCover = true;
			for (int j = 0; j < b.gens.size(); j++) {
				HashMap<Integer, Integer> coverb = b.covers.get(j);
				if (!covera.keySet().containsAll(coverb.keySet())) {
					supCover = false;
					break;
				}
				for (Integer v : coverb.keySet()) {
					if (covera.get(v) > coverb.get(v)) {
						supCover = false;
						break;
					}
				}
				if (!supCover) break;
			}
			if (supCover) return true;
		}
		return false;
	}
	
	private static Pair<Integer, ArrayList<PopEntry>> getWorkList(PriorityQueue<PopEntry> pop, 
			HashMap<Integer, Integer> shareM, int popLimit) {
		ArrayList<PopEntry> ret = new ArrayList<PopEntry>();
		ArrayList<PopEntry> backs = new ArrayList<PopEntry>();
		// We first try to get all the guys that covers unique cases
		ArrayList<PopEntry> tmp = new ArrayList<PopEntry>();
		shareM.clear();
		while (pop.size() > 0) {
			PopEntry e = pop.poll();
			boolean unique = false;
			for (HashMap<Integer, Integer> m : e.covers) {
				for (Integer k : m.keySet()) {
					if (!shareM.containsKey(k)) {
						unique = true;
						break;
					}
				}
			}
			if (unique) {
				ret.add(e);
				for (HashMap<Integer, Integer> m : e.covers) 
					for (Entry<Integer, Integer> ent : m.entrySet()) {
						if (shareM.containsKey(ent.getKey()))
							shareM.put(ent.getKey(), shareM.get(ent.getKey()) + 1);
						else
							shareM.put(ent.getKey(), 1);
					}
				if (ret.size() >= popLimit) break;
			}
			else
				tmp.add(e);
		}
		System.out.println("Core Population Size: " + ret.size());
		System.out.println("ShareM: " + shareM.toString());
		int coreSize = ret.size();
		// Then we try to get all the guys that is not completely shadowed by others
		if (ret.size() < popLimit) {
			for (PopEntry e : tmp) {
				if (e == null) break;
				boolean use = true;
				for (int j = 0; j < ret.size(); j++) {
					if (weakDominate(ret.get(j), e)) {
						use = false;
						break;
					}
				}
				if (use) {
					ret.add(e);
					if (ret.size() >= popLimit) break;
				}
				else
					backs.add(e);
			}
			// Then in the end we get all the guys that is not completely dominated in cost
			// If we still have room
			if (ret.size() < popLimit) {
				ArrayList<PopEntry> backs2 = new ArrayList<PopEntry>();
				for (int i = 0; i < popLimit - ret.size(); i++) {
					if (backs.size() <= i) break;
					PopEntry e = backs.get(i);
					boolean use = true;
					for (int j = 0; j < ret.size(); j++)
						if (strongDominate(ret.get(j), e)) {
							use = false;
							break;
						}
					if (use) 
						ret.add(e);
					else
						backs2.add(e);
				}
				// XXX: Just try to see whether this helps
				if (ret.size() < popLimit) {
					for (int i = 0; i < popLimit - ret.size(); i++) {
						if (backs2.size() <= i) break;
						PopEntry e = backs2.get(i);
						ret.add(e);
					}
				}
			}
		}
		return new Pair<Integer, ArrayList<PopEntry>>(coreSize, ret);
	}
	
	private static ArrayList<CodeTransform> computeGenerators(HashMap<Integer, Integer> m,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<CorpusPatch> patchInfo, int n1, int n2) {
		// empty set may cause crash
		if (m.size() == 0)
			return null;
		CodeTransAbstractor abst = new CodeTransAbstractor();
		for (Entry<Integer, Integer> e: m.entrySet()) {
			DecomposedCodePair p = trainDB.get(e.getKey()).get(e.getValue());
			abst.addMapping(p.insides, p.before, p.after);
		}
		boolean succ = abst.generalize();
		if (!succ) return null;
		ArrayList<CodeTransform> ret = new ArrayList<CodeTransform>();
		int n = abst.getNumGenerators();
		for (int i = 0; i < n; i++) {
			ret.add(abst.getGenerator(i));
		}
		return ret;
	}
	
	private static PopEntry computePopEntry(HashMap<Integer, Integer> m, ArrayList<ArrayList<DecomposedCodePair>> trainDB,
			ArrayList<CorpusPatch> patchInfo, HashMap<Integer, Integer> shareM, int n1, int n2) {
		int bestFitness = -1;
		ArrayList<CodeTransform> gens = computeGenerators(m, trainDB, patchInfo, n1, n2);
		if (gens == null) {
			return null;
		}
		ArrayList<HashMap<Integer, Integer>> covers = new ArrayList<HashMap<Integer, Integer>>(); 
		for (CodeTransform gen : gens) {
			HashMap<Integer, Integer> cover = computeCoverSet(gen, trainDB, patchInfo, n1, n2, false);
			covers.add(cover);
			int fitness = computeFitness(cover, shareM, n1);
			if (bestFitness == -1 || bestFitness < fitness) 
				bestFitness = fitness;
		}
		return new PopEntry(m, gens, covers, bestFitness);
	}

	private static HashMap<Integer, Integer> computeCoverSet(CodeTransform gen,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<CorpusPatch> patchInfo, int n1, int n2, boolean printout) {
		HashMap<Integer, Integer> ret = new HashMap<Integer, Integer>();
		for (int i = 0; i < n2; i++) {
			int bestCost = -1;
			ArrayList<DecomposedCodePair> tmp = trainDB.get(i);
			if (printout) {
				System.out.println("Check cover for pair: " + i);	
			}
			for (int j = 0; j < tmp.size(); j++) {
				DecomposedCodePair p = tmp.get(j);
				CodeTransAdapter adapter = new CodeTransAdapter(gen, p.before.getFactory());
				if (adapter.checkInside(p.insides))
					if (adapter.applyTo(p.before))
						if (adapter.covers(p.after)) {
							//System.out.println("[DEBUG] Covers: " + i + " " + j);
							int cost = (int)adapter.estimateCost();
							if (cost > 0) {
								if (bestCost == -1 || cost < bestCost) {
									bestCost = cost;
								}
							}
						}
			}
			if (bestCost != -1) {
				ret.put(i, bestCost);
			}
		}
		return ret;
	}
	
	private static int computeFitness(HashMap<Integer, Integer> s, HashMap<Integer, Integer> shareM, int n1) {
		int fitness = 0;
		for (Entry<Integer, Integer> e : s.entrySet()) {
			int idx = e.getKey();
			int fitnessBonus = 0;
			if (e.getValue() < FitnessValidateBonus)
				fitnessBonus = FitnessValidateBonus - e.getValue();
			else
				continue;
			
			if (idx >= n1) {
				if (shareM.containsKey(idx))
					fitnessBonus = fitnessBonus / shareM.get(idx);
			}
			else {
				if (shareM.containsKey(idx))
					fitnessBonus = fitnessBonus / (shareM.get(idx) * FitnessTrainRatio);
				else
					fitnessBonus = fitnessBonus / FitnessTrainRatio;
			}
			fitness += fitnessBonus;
			//if (e.getValue() < fitnessBonus)
				//fitness += fitnessBonus - e.getValue();
			/*if (idx < n1) {
				if (e.getValue() < FitnessBaseBonus)
					fitness += FitnessBonusInTrain - e.getValue();
			}
			else
				fitness += Config.costlimit * 2 - e.getValue();*/
		}
		return fitness;
	}
	
	static class MutateTask implements Runnable {
		private ArrayList<ArrayList<DecomposedCodePair>> trainDB;
		private ArrayList<CorpusPatch> patchInfo;
		private PopEntry e;
		private Set<HashMap<Integer, Integer>> popSet;
		private HashMap<Integer, Integer> shareM;
		private Table<Integer, Integer, ArrayList<Integer>> graph;
		private int n1, n2;
		ArrayList<PopEntry> pop;
		
		MutateTask(ArrayList<ArrayList<DecomposedCodePair>> trainDB,
				ArrayList<CorpusPatch> patchInfo,				
				HashMap<Integer, Integer> shareM,
				PopEntry e, Set<HashMap<Integer, Integer>> popSet,
				Table<Integer, Integer, ArrayList<Integer>> graph,
				int n1, int n2) {
			this.trainDB = trainDB;
			this.patchInfo = patchInfo;
			this.e = e;
			this.popSet = popSet;
			this.shareM = shareM;
			this.graph = graph;
			this.n1 = n1;
			this.n2 = n2;
			this.pop = null;
		}
		
		@Override
		public void run() {
			pop = new ArrayList<PopEntry>();
			System.out.println("Evolving: " + e.m.toString() + " fitness " + e.fitness);
			System.out.println("Covers: " + e.covers.toString());
			int bestcost = -1;
			HashMap<Integer, ArrayList<Integer>> candidateM = new HashMap<Integer, ArrayList<Integer>>();
			for (Integer i : e.m.keySet()) {
				addToCandidateMap(candidateM, graph, i, e.m.get(i));
			}
			for (Integer i : candidateM.keySet()) {
				if (e.m.containsKey(i)) continue;
				// Skip a covered case
				if (e.covers.size() == 1)
					if (e.covers.get(0).containsKey(i)) continue;
				for (Integer j : candidateM.get(i)) {
					HashMap<Integer, Integer> tmpm = new HashMap<Integer, Integer>(e.m);
					tmpm.put(i, j);
					if (!popSet.contains(tmpm)) {
						PopEntry newEntry = computePopEntry(tmpm, trainDB, patchInfo, shareM, n1, n2);
						if (newEntry != null && newEntry.fitness > 0) {
							pop.add(newEntry);
							if (newEntry.fitness > bestcost) bestcost = newEntry.fitness;
							popSet.add(tmpm);
						}
					}
				}
			}
			if (enableCrossover) {
				// We do not remove elements if we are not doing crossover, there is
				// no point to do it
				for (Integer v : e.m.keySet()) {
					HashMap<Integer, Integer> tmpm = new HashMap<Integer, Integer>(e.m);
					tmpm.remove(v);
					if (!popSet.contains(tmpm)) {
						PopEntry newEntry = computePopEntry(tmpm, trainDB, patchInfo, shareM, n1, n2);
						if (newEntry != null && newEntry.fitness > 0) { 
							pop.add(newEntry);
							if (newEntry.fitness > bestcost) bestcost = newEntry.fitness;
							popSet.add(tmpm);
						}
					}
				}
			}
			System.out.println("Best fitness: " + bestcost);
		}
	}
	
	static class CrossoverTask implements Runnable {
		private ArrayList<ArrayList<DecomposedCodePair>> trainDB;
		private ArrayList<CorpusPatch> patchInfo;
		private HashMap<Integer, Integer> shareM;
		private HashMap<Integer, Integer> common;
		private HashMap<Integer, Integer> flip;
		private Set<HashMap<Integer, Integer>> popSet;
		private int n1, n2;
		ArrayList<PopEntry> pop;
		
		CrossoverTask(ArrayList<ArrayList<DecomposedCodePair>> trainDB,
				ArrayList<CorpusPatch> patchInfo,
				HashMap<Integer, Integer> shareM,
				HashMap<Integer, Integer> common,
				HashMap<Integer, Integer> flip,
				Set<HashMap<Integer, Integer>> popSet, 
				int n1, int n2) {
			this.trainDB = trainDB;
			this.patchInfo = patchInfo;
			this.shareM = shareM;
			this.common = common;
			this.flip = flip;
			this.popSet = popSet;
			this.n1 = n1;
			this.n2 = n2;
			this.pop = null;
		}
		
		@Override
		public void run() {
			// Crossover shit? I hate genetic programming magic
			pop = new ArrayList<PopEntry>();
			Random rand = new Random(System.identityHashCode(common));
			int round = CrossoverRound * flip.size();
			int bestcost = -1;
			System.out.println("Corssover: " + common.toString() + " " + flip.toString());
			for (int cnt = 0; cnt < round; cnt++) {
				HashMap<Integer, Integer> tmpm0 = new HashMap<Integer, Integer>(common);
				HashMap<Integer, Integer> tmpm1 = new HashMap<Integer, Integer>(common);
				for (Entry<Integer, Integer> e : flip.entrySet()) {
					if (rand.nextBoolean()) {
						tmpm0.put(e.getKey(), e.getValue());
					}
					else
						tmpm1.put(e.getKey(), e.getValue());
					if (!popSet.contains(tmpm0)) {
						PopEntry newEntry = computePopEntry(tmpm0, trainDB, patchInfo, shareM, n1, n2);
						if (newEntry != null && newEntry.fitness > 0) { 
							pop.add(newEntry);
							if (newEntry.fitness > bestcost) bestcost = newEntry.fitness;
							popSet.add(tmpm0);
						}
					}
					if (!popSet.contains(tmpm1)) {
						PopEntry newEntry = computePopEntry(tmpm1, trainDB, patchInfo, shareM, n1, n2);
						if (newEntry != null && newEntry.fitness > 0) { 
							pop.add(newEntry);
							if (newEntry.fitness > bestcost) bestcost = newEntry.fitness;
							popSet.add(tmpm1);
						}
					}
				}
			}
			System.out.println("Best fitness: " + bestcost);
		}
	}
	
	static class InitializeTask implements Runnable {
		private ArrayList<ArrayList<Integer>> initial;
		private ArrayList<ArrayList<DecomposedCodePair>> trainDB;
		private HashMap<Integer, Integer> shareM;
		private ArrayList<CorpusPatch> patchInfo;
		PopEntry popEnt;
		int n1, n2, i;
		
		InitializeTask(ArrayList<ArrayList<Integer>> initial,
				ArrayList<ArrayList<DecomposedCodePair>> trainDB,
				ArrayList<CorpusPatch> patchInfo,
				HashMap<Integer, Integer> shareM,
				int n1, int n2, int i) {
			this.initial = initial;
			this.trainDB = trainDB;
			this.patchInfo = patchInfo;
			this.shareM = shareM;
			this.popEnt = null;
			this.n1 = n1;
			this.n2 = n2;
			this.i = i;
		}
		
		@Override
		public void run() {
			ArrayList<Integer> a = initial.get(i);
			HashMap<Integer, Integer> tmp = new HashMap<Integer, Integer>();
			if (a.size() > 5) {
				tmp.put(a.get(1), a.get(2));
				tmp.put(a.get(3), a.get(4));
				tmp.put(a.get(5), a.get(6));
				System.out.println(a.get(1) + " " + a.get(3) + " " + a.get(5));
				System.out.println(a.get(2) + " " + a.get(4) + " " + a.get(6));
			}
			else {
				tmp.put(a.get(1), a.get(2));
				tmp.put(a.get(3), a.get(4));
				System.out.println(a.get(1) + " " + a.get(3));
				System.out.println(a.get(2) + " " + a.get(4));
			}
			popEnt = computePopEntry(tmp, trainDB, patchInfo, shareM, n1, n2);
			if (popEnt != null) {
				System.out.println(popEnt.fitness);
				System.out.println(popEnt.covers.toString());
			}
			else
				System.out.println("Generation failed. NULL! " + a.toString());
		}
	}
	
	public static ArrayList<PopEntry> geneticProg(ArrayList<ArrayList<Integer>> initial,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<CorpusPatch> patchInfo,
			Table<Integer, Integer, ArrayList<Integer>> graph,
			int n1, int n2) {
		HashMap<Integer, Integer> shareM = new HashMap<Integer, Integer>();
		ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
		ArrayList<InitializeTask> tasks0 = new ArrayList<InitializeTask>();
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>();
		for (int i = 0; i < initial.size(); i++) {
			InitializeTask t = new InitializeTask(initial, trainDB, patchInfo, shareM, n1, n2, i);
			futures.add(pool.submit(t));
			tasks0.add(t);
		}
		pool.shutdown();
		while (!pool.isTerminated()) {
			try {
				pool.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		for (int i = 0; i < futures.size(); i++) {
			try {
				if (futures.get(i).isDone())
					futures.get(i).get();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			catch (ExecutionException e) {
				System.out.println("Execution ERROR for Initial Task " + tasks0.get(i).i + ": " + initial.get(tasks0.get(i).i) + "!!!!!!!");
				System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
			}
		}
		PriorityQueue<PopEntry> pop = new PriorityQueue<PopEntry>(PopLimit, new PopEntryComparator());
		for (InitializeTask t : tasks0) {
			if (t.popEnt != null)
				pop.add(t.popEnt);
		}
		pool = null;
		tasks0 = null;
		
		for (int round = 0; round < RoundCount; round ++) {
			System.out.println("ROUND: " + round);
			Pair<Integer, ArrayList<PopEntry>> tmpP = getWorkList(pop, shareM, PopLimit);
			int coreSize = tmpP.x;
			ArrayList<PopEntry> workList = tmpP.y;
			writePopulationToFile(workList, "/tmp/before-round-" + round + ".log", true);
			
			pop = null;
			
			HashSet<Integer> tmpCoverS = new HashSet<Integer>();
			for (PopEntry e : workList) {
				for (HashMap<Integer, Integer> c : e.covers)
					tmpCoverS.addAll(c.keySet());
			}
			int cnt = 0;
			for (Integer x : tmpCoverS)
				if (x >= n1)
					cnt++;
			System.out.println(tmpCoverS.toString());
			System.out.println("Worklist Covered: " + tmpCoverS.size());
			System.out.println("Worklist Covered Validated: " + cnt);
			
			System.out.println("Population Size: " + workList.size());
			pool = Executors.newFixedThreadPool(ThreadCount);
			Set<HashMap<Integer, Integer>> popSet = Collections.newSetFromMap(
					new ConcurrentHashMap<HashMap<Integer, Integer>, Boolean>());
			ArrayList<MutateTask> tasks = new ArrayList<MutateTask>();
			futures.clear();
			for (PopEntry e : workList) {
				MutateTask t = new MutateTask(trainDB, patchInfo, shareM, e, popSet, graph, n1, n2);
				futures.add(pool.submit(t));
				tasks.add(t);
			}
			pool.shutdown();
			while (!pool.isTerminated()) {
				try {
					pool.awaitTermination(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			for (int i = 0; i < futures.size(); i++) {
				try {
					if (futures.get(i).isDone())
						futures.get(i).get();
				}
				catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				catch (ExecutionException e) {
					System.out.println("Execution ERROR for Mutate Task " + tasks.get(i).e.m + "!!!!!!!");
					System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
				}
			}
			pop = new PriorityQueue<PopEntry>(PopLimit, new PopEntryComparator());
			for (MutateTask t : tasks) {
				pop.addAll(t.pop);
			}
			tasks = null;
			if (enableCrossover) {
				ArrayList<CrossoverTask> tasks2 = new ArrayList<CrossoverTask>();
				futures.clear();
				pool = Executors.newFixedThreadPool(ThreadCount);
				for (int i = 0; i < coreSize; i++)
					for (int j = i + 1; j < coreSize; j++) {
						PopEntry e1 = workList.get(i);
						PopEntry e2 = workList.get(j);
						HashMap<Integer, Integer> common = new HashMap<Integer, Integer>();
						HashMap<Integer, Integer> flip = new HashMap<Integer, Integer>();
						boolean failed = false;
						for (Integer k : e1.m.keySet()) {
							if (e2.m.containsKey(k)) {
								int v1 = e1.m.get(k);
								int v2 = e2.m.get(k);
								if (v1 != v2) {
									failed = true;
									break;
								}
								else
									common.put(k, v1);
							}
							else
								flip.put(k, e1.m.get(k));
						}
						if (failed) continue;
						for (Integer k : e2.m.keySet()) {
							if (!e1.m.containsKey(k))
								flip.put(k, e2.m.get(k));
						}
						if (common.size() < 1) continue;
						if (flip.size() < 3) continue;
						CrossoverTask t = new CrossoverTask(trainDB, patchInfo, shareM, common, flip, popSet, n1, n2);
						tasks2.add(t);
						futures.add(pool.submit(t));
					}
				System.out.println("Total Crossover tasks: " + tasks2.size());
				pool.shutdown();
				while (!pool.isTerminated()) {
					try {
						pool.awaitTermination(10, TimeUnit.SECONDS);
					}
					catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
				for (int i = 0; i < futures.size(); i++) {
					try {
						if (futures.get(i).isDone())
							futures.get(i).get();
					}
					catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
					catch (ExecutionException e) {
						System.out.println("Execution ERROR for Crossover Task " + tasks2.get(i).common + " " + tasks2.get(i).flip + "!!!!!!!");
						System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
					}
				}
				for (CrossoverTask t : tasks2) {
					pop.addAll(t.pop);
				}
				pool = null;
				tasks2 = null;
			}
			
			// XXX: Try to add old generation
			for (PopEntry e : workList) {
				if (!popSet.contains(e.m))
					pop.add(e);
			}
			
			System.out.println("ROUND FINISH, Population Size: " + pop.size());
			
			HashSet<Integer> tmpCoverE = new HashSet<Integer>();
			for (PopEntry e : pop) {
				for (HashMap<Integer, Integer> c : e.covers)
					tmpCoverE.addAll(c.keySet());
			}
			cnt = 0;
			for (Integer x : tmpCoverE)
				if (x >= n1)
					cnt++;
			System.out.println(tmpCoverE.toString());
			System.out.println("PopCovered: " + tmpCoverE.size());
			System.out.println("PopCovered Validated: " + cnt);
			HashSet<Integer> gain = new HashSet<Integer>(tmpCoverE);
			gain.removeAll(tmpCoverS);
			System.out.println("Gain: " + gain.toString());
			HashSet<Integer> loss = new HashSet<Integer>(tmpCoverS);
			loss.removeAll(tmpCoverE);
			System.out.println("Loss: " + loss.toString());
		}
		
		ArrayList<PopEntry> ret = getWorkList(pop, shareM, PopLimit).y;				
		return ret;
	}

	static class FetchTask implements Runnable {
		ArrayList<CorpusPatch> patchInfo;
		String dataPath;
		int i;
		Pair<MyCtNode, MyCtNode> codePair;
		ArrayList<DecomposedCodePair> decomposedPairs;
		
		FetchTask(ArrayList<CorpusPatch> patchInfo, String dataPath, int i) {
			this.patchInfo = patchInfo;
			this.dataPath = dataPath;
			this.i = i;
			this.codePair = null;
			this.decomposedPairs = null;
		}
		
		@Override
		public void run() {
			CorpusPatch p = patchInfo.get(i);
			codePair = new Pair<MyCtNode, MyCtNode>(CorpusUtils.parseJavaAST(dataPath + "/" + p.prePath), 
					CorpusUtils.parseJavaAST(dataPath + "/" + p.postPath));
			CodePairDecomposer decomposer = new CodePairDecomposer(codePair.x, codePair.y);
			decomposedPairs = decomposer.decompose();
		}
	};
	
	private static void writePopulationToFile(ArrayList<PopEntry> pop, String path, boolean text) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(path)));
			for (PopEntry e : pop) {
				writer.write(e.m.size() + " ");
				for (Entry<Integer, Integer> ent : e.m.entrySet()) {
					writer.write(ent.getKey() + " " + ent.getValue() + " ");
				}
				writer.newLine();
				if (text) {
					if (e.gens != null) {
						for (int i = 0; i < e.gens.size(); i++) {
							CodeTransform gen = e.gens.get(i);
							writer.write(gen.toString());
							writer.newLine();
							writer.write("Covers:\n");
							writer.write(e.covers.get(i).toString());
							writer.newLine();
						}
					}
					writer.write("======================================================");
					writer.newLine();
				}
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	private static void writeCandidateCoverToFile(ArrayList<CodeTransform> gens, ArrayList<HashMap<Integer, Integer> > m, ArrayList<HashSet<Integer>> covers, 
			String path, String candidatePath, boolean b) {
		File f = new File(path);
		File cand = new File(candidatePath);
		try {
			if (!cand.exists())
				Files.createDirectory(Paths.get(candidatePath));
			BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			int cnt = 0;
			for (int i = 0; i < gens.size(); i++) {
				CodeTransform gen = gens.get(i);
				String genPath = candidatePath + "/gen" + cnt + ".po";
				writer.write(genPath);
				writer.newLine();
				writer.write(m.get(i).toString());
				writer.newLine();
				HashSet<Integer> cover = covers.get(i);
				writer.write(cover.size() + " ");
				for (Integer v : cover) {
					writer.write(v + " ");
				}
				writer.newLine();
				File candf = new File(genPath);
				FileOutputStream fos = new FileOutputStream(candf);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(gen);
				oos.close();
				cnt ++;
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void readCandidateCover(File f, File candDir, ArrayList<CodeTransform> gens, ArrayList<HashSet<Integer>> covers) {
		gens.clear();
		covers.clear();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(f));
			String line;
			while ((line = reader.readLine()) != null) {
				String fname = line.trim();
				FileInputStream fis = new FileInputStream(fname);
				ObjectInputStream ios = new ObjectInputStream(fis);
				gens.add((CodeTransform) ios.readObject());
				ios.close();
				// This line is for generation map log, skipped
				line = reader.readLine();
				line = reader.readLine();
				Scanner s = new Scanner(line);
				int n = s.nextInt();
				HashSet<Integer> hs = new HashSet<Integer>();
				for (int i = 0; i < n; i++)
					hs.add(s.nextInt());
				s.close();
				covers.add(hs);
			}
			reader.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static class CostMetricTask implements Runnable {

		CodeTransform gen;
		ArrayList<DecomposedCodePair> pairs;
		String taskName;
		long sum;
		
		public CostMetricTask(CodeTransform gen,
				ArrayList<DecomposedCodePair> pairs, String taskName) {
			this.gen = gen;
			this.pairs = pairs;
			this.taskName = taskName;
			this.sum = 0;
		}
		
		@Override
		public void run() {
			ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode> > candidateBefores = obtainCandidateBefores(pairs);
			for (Pair<HashSet<MyNodeSig>, MyCtNode> p : candidateBefores) {
				CodeTransAdapter adapter = new CodeTransAdapter(gen, p.y.getFactory());
				if (adapter.checkInside(p.x))
					if (adapter.applyTo(p.y)) {
						long v = adapter.estimateCost();
						if (v < 0) {
							sum = Config.sizeCap;
							break;
						}
						else {
							long realV = adapter.prepareGenerate();
							sum += realV;
							if (sum > Config.sizeCap) {
								sum = Config.sizeCap;
								break;
							}
							/*else {
								for (int i = 0; i < realV; i++) {
									adapter.generateOne();
									if (adapter.passTypecheck())
										sum ++;
								}
							}*/
						}
					}
			}
			System.out.println("Task finished: " + taskName + " " + sum);
		}
		
	}
	
	private static void computeCostMetric(ArrayList<CodeTransform> candidates,
			ArrayList<ArrayList<DecomposedCodePair>> trainDB, ArrayList<HashSet<Integer>> covers, int n1, int n2, File file) {
		ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
		ArrayList<CostMetricTask> tasks = new ArrayList<CostMetricTask>();
		ArrayList<Pair<Integer, Integer>> taskIdxs = new ArrayList<Pair<Integer, Integer>>();
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>();
		int candSize = candidates.size();
		
		HashSet<Integer> coverM = new HashSet<Integer>();
		for (HashSet<Integer> s : covers)
			coverM.addAll(s);
		
		int s = n1;
		if (ComputeFullCost) s = 0;
		
		for (int i = s; i < trainDB.size(); i++) {
			// Skip all that is not covered
			if (i < n2)
				if (!coverM.contains(i)) continue;
			ArrayList<DecomposedCodePair> pairs = trainDB.get(i);
			for (int j = 0; j < candSize; j++) {
				CodeTransform gen = candidates.get(j);
				CostMetricTask t = new CostMetricTask(gen, pairs, "Cost-" + i + "-" + j);
				futures.add(pool.submit(t));
				tasks.add(t);
				taskIdxs.add(new Pair<Integer, Integer>(i, j));
			}
		}
		pool.shutdown();
		while (!pool.isTerminated()) {
			try {
				pool.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		for (int i = 0; i < futures.size(); i++) {
			Future<?> f = futures.get(i);
			try {
				if (f.isDone())
					f.get();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			catch (ExecutionException e) {
				System.out.println("Execution ERROR for TASK " + tasks.get(i).taskName + "!!!!!!!");
				System.out.println(ExceptionUtils.getStackTrace(e.getCause()));
			}
		}
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(file));
			writer.write(trainDB.size() + " " + n1 + " " + n2 + " " + candSize);
			writer.newLine();
			int cost[][];
			cost = new int[trainDB.size()][candSize];
			for (int i = 0; i < trainDB.size(); i ++)
				for (int j = 0; j < candSize; j++)
					cost[i][j] = 0;
			for (int i = 0; i < tasks.size(); i++) {
				CostMetricTask t = tasks.get(i);
				Pair<Integer, Integer> taskp = taskIdxs.get(i);
				cost[taskp.x][taskp.y] = (int)t.sum;
			}
			for (int i = 0; i < trainDB.size(); i++) {
				for (int j = 0; j < candSize; j++)
					writer.write(cost[i][j] + " ");
				writer.newLine();
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> obtainCandidateBefores(
			ArrayList<DecomposedCodePair> pairs) {
		// We first find the statement that will be identified as the statement to fix.
		// We find the last CtBlock in the chain and the next statement is what we are looking for
		// If not, we are going to just stick with the last CtStatement we find out
		int parentBlockIdx = -1;
		for (int i = 0; i < pairs.size(); i++) {
			if (pairs.get(i).before.isEleClass(CtStatementList.class)) {
				parentBlockIdx = i; 
			}
		}
		int stmtIdx = -1;
		int colStmtIdx = -1;
		// We get a Block index, we are going to find the next CtStatement and that will be 
		// What we are looking for
		if (parentBlockIdx != -1) {
			for (int i = parentBlockIdx + 1; i < pairs.size(); i++) {
				if (pairs.get(i).before.isEleClass(CtStatement.class)) {
					stmtIdx = i;
					break;
				}
				if (pairs.get(i).before.isColClass(CtCodeElementImpl.class))
					colStmtIdx = i;
			}
		}
		if (stmtIdx == -1) {
			if (colStmtIdx == -1) {
				// We are in big problem, we cannot find any statement here.
				// I am just going to assume the last statement is what we are going to work on
				for (int i = pairs.size() - 1; i > 0; i--) {
					if (pairs.get(i).before.isEleClass(CtStatement.class)) {
						stmtIdx = i;
						break;
					}
				}
			}
			else {
				MyCtNode nd = pairs.get(colStmtIdx).before;
				if (nd.getNumChildren() == 0)
					return new ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>>();
				else {
					// before's first statement is the most likely place 
					return ASTNodeCollector.getCandidateNodes(pairs.get(colStmtIdx).insides, nd.getChild(0));
				}
			}
			if (stmtIdx == -1) {
				return new ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>>();
			}
			
			/*parentBlockIdx = -1;
			for (int i = stmtIdx - 1; i > 0; i--) {
				if (pairs.get(i).before.isEleClass(CtStatementList.class)) {
					parentBlockIdx = i;
					break;
				}
			}*/
		}
		
		return ASTNodeCollector.getCandidateNodes(pairs.get(stmtIdx).insides, pairs.get(stmtIdx).before);
		
		/*
		ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> ret = new ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>>();
		if (parentBlockIdx != -1) {
			// If we have a parent block statement, I am going to count all collection of statements as well
			ArrayList<MyCtNode> colRet = obtainCandidateColStatement(pairs.get(parentBlockIdx).before, pairs.get(stmtIdx).before);
			for (MyCtNode node : colRet) {
				ret.add(new Pair<HashSet<MyNodeSig>, MyCtNode>(pairs.get(stmtIdx).insides, node));
			}
		}
	
		ElementCollector collec = new ElementCollector(pairs.get(stmtIdx).insides);
		pairs.get(stmtIdx).before.acceptScanner(collec);
		ret.addAll(collec.res);
		
		return ret;*/
	}

	public static void main(String args[]) {
		if (args.length < 5) {
			System.out.println("<CorpusMode 1:NPE 2:OOB 3:CCE 4:ALL> <DataPath> <TotalFetch> <LearnSize> <TrainSize> [ThreadCount]");
            System.exit(1);
		}
		int corpusMode = Integer.parseInt(args[0]);
		String dataPath = args[1];
		int totFetch = 0;
		int totLearn = 0;
		int totTrain = 0;
		try {
			totFetch = Integer.parseInt(args[2]);
			totLearn = Integer.parseInt(args[3]);
			totTrain = Integer.parseInt(args[4]);
			if (args.length > 5)
				ThreadCount = Integer.parseInt(args[5]);
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			System.out.println("Illegal arguments!");
			System.exit(1);
		}
		System.out.println("TotalFetch: " + totFetch);
		System.out.println("TotalTrain: " + totLearn);
		System.out.println("ThreadCount: " + ThreadCount);
		CodeCorpusDB corpus = new CodeCorpusDB();
		corpus.shuffleInitFetch(corpusMode);
		ArrayList<CorpusPatch> patchInfo = new ArrayList<CorpusPatch>(corpus.fetch(totFetch));
		
		ArrayList<FetchTask> tasks = new ArrayList<FetchTask>();
		ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);
		for (int i = 0; i < patchInfo.size(); i++) {
			FetchTask t = new FetchTask(patchInfo, dataPath, i);
			pool.submit(t);
			tasks.add(t);
		}
		pool.shutdown();
		while (!pool.isTerminated()) {
			try {
				pool.awaitTermination(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		ArrayList<Pair<MyCtNode, MyCtNode>> codePairs = new ArrayList<Pair<MyCtNode, MyCtNode>>();
		ArrayList<ArrayList<DecomposedCodePair>> trainDB =
				new ArrayList<ArrayList<DecomposedCodePair>>();
		for (FetchTask t : tasks) {
			codePairs.add(t.codePair);
			trainDB.add(t.decomposedPairs);
		}
		pool = null;
		tasks = null;
		
		System.out.println("The total number of parsed tree pairs: " + codePairs.size());
		
		int n1 = totLearn;
		int n2 = totTrain;
		int n3 = totFetch;
		
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File("/tmp/patchinfo.log")));
			HashMap<Integer, CorpusApp> apps = corpus.getApplications();
			for (int i = 0; i < n3; i++) {
				CorpusPatch info = patchInfo.get(i);
				writer.write(Integer.toString(i));
				writer.newLine();
				writer.write(apps.get(info.appId).accname + "/" + apps.get(info.appId).reponame + " " + info.postRev);
				writer.newLine();
				writer.write(apps.get(info.appId).githubUrl + "/commit/" + info.postRev);
				writer.newLine();
			}
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("First round of pair counting!");
		Table<Integer, Integer, ArrayList<Integer>> res = null;
		File round1f = new File("/tmp/round1.log");
		if (round1f.exists() && !round1f.isDirectory()) 
			res = readRound1Res(round1f);
		else {
			res = computeRound1Res(trainDB, patchInfo, n1);
			writeRound1Res(res, round1f);
		}
		
		System.out.println("First round finished, total " + res.size());
		
		Table<Integer, Integer, ArrayList<Integer>> graph = HashBasedTable.create();
		for (Cell<Integer, Integer, ArrayList<Integer>> c : res.cellSet()) {
			int a1 = c.getRowKey();
			int b1 = c.getColumnKey();
			int a2 = c.getValue().get(1);
			int b2 = c.getValue().get(2);
			if (!graph.contains(a1, a2)) {
				graph.put(a1, a2, new ArrayList<Integer>());
			}
			ArrayList<Integer> tmp = graph.get(a1, a2);
			tmp.add(b1);
			tmp.add(b2);
			if (!graph.contains(b1, b2)) {
				graph.put(b1, b2, new ArrayList<Integer>());
			}
			tmp = graph.get(b1, b2);
			tmp.add(a1);
			tmp.add(a2);
		}
	
		ArrayList<ArrayList<Integer>> res0 = null;
		
		if (SkipRound2) {
			res0 = new ArrayList<ArrayList<Integer>>();
			for (Cell<Integer, Integer, ArrayList<Integer>> c : res.cellSet()) {
				ArrayList<Integer> tmp = new ArrayList<Integer>();
				tmp.add(c.getValue().get(0));
				tmp.add(c.getRowKey());
				tmp.add(c.getValue().get(1));
				tmp.add(c.getColumnKey());
				tmp.add(c.getValue().get(2));
				res0.add(tmp);
			}
		}
		else {
			File round2f = new File("/tmp/round2.log");
			if (round2f.exists() && !round2f.isDirectory())
				res0 = readRound2Res(round2f);
			else {
				res0 = computeRound2Res(res, trainDB, patchInfo, graph, n1);
				writeRound2Res(res0, round2f);
			}
			
			System.out.println("Second round finished with " + res0.size() + " triples, written to round2.log.");
		}
		
		File round3f = new File("cover.txt");
		ArrayList<CodeTransform> candidates = new ArrayList<CodeTransform>();
		ArrayList<HashSet<Integer>> covers = new ArrayList<HashSet<Integer>>();
		if (round3f.exists()) 
			readCandidateCover(round3f, new File("candidate"), candidates, covers);
		else{
			ArrayList<PopEntry> res2 = geneticProg(res0, trainDB, patchInfo, graph, n1, n2);
			ArrayList<HashMap<Integer, Integer>> tmpm = new ArrayList<HashMap<Integer, Integer>>();
			for (PopEntry e : res2) {
				candidates.addAll(e.gens);
				for (int i = 0; i < e.gens.size(); i++)
					tmpm.add(e.m);
			}
			for (int i = 0; i < candidates.size(); i++) {
				CodeTransform gen = candidates.get(i);
				System.out.println("Compute cover for generator M: " + tmpm.get(i));
				HashMap<Integer, Integer> m = computeCoverSet(gen, trainDB, patchInfo, n1, n3, true);
				covers.add(new HashSet<Integer>(m.keySet()));
			}
			writeCandidateCoverToFile(candidates, tmpm, covers, "cover.txt", "candidate", false);
		}
		
		System.out.println("GeneticProg round finished with " + candidates.size() + " candidates, written to cover.txt candidate/.");
		
		computeCostMetric(candidates, trainDB, covers, n1, n2, new File("cost.txt"));
		
		System.out.println("Compute cost metric complete!");
	}
}
