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
package genesis.space;

import java.io.File;


import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import com.google.common.io.Files;

import genesis.GenesisException;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.space.par.*;
import genesis.transform.CodeTransAdapter;
import genesis.transform.CodeTransform;

public class SearchSpace {
	ArrayList<String> paths;
	ArrayList<String> dirs;
	ArrayList<Integer> sidxs;
	ArrayList<Integer> gidxs;
	ArrayList<CodeTransform> transforms;
	int failedCheckCnt;

	public SearchSpace() {
		paths = new ArrayList<String>();
		dirs = new ArrayList<String>();
		sidxs = new ArrayList<Integer>();
		gidxs = new ArrayList<Integer>();
		transforms = new ArrayList<CodeTransform>();
		failedCheckCnt = 0;
	}
	
	public SearchSpace(String[] spacePaths, String[] candidateDirs) {
		this();
		int m = spacePaths.length;
		for (int k = 0; k < m; k++) {
			String spacePath = spacePaths[k];
			String candidateDir = candidateDirs[k];
			paths.add(spacePath);
			dirs.add(candidateDir);
			String spaceLine = null;
			try {
				spaceLine = Files.readFirstLine(new File(spacePath), Charset.defaultCharset());
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new GenesisException("Unable to parse the search space file: " + spacePath);
			}
			Scanner s = new Scanner(spaceLine);
			int n = s.nextInt();
			ArrayList<Integer> idxs = new ArrayList<Integer>();
			for (int i = 0; i < n; i++) {
				int idx = s.nextInt();
				idxs.add(idx);
			}
			for (int i = 0; i < n - 1; i++)
				for (int j = i + 1; j < n; j++)
					if (idxs.get(i) > idxs.get(j)) {
						int tmp = idxs.get(i);
						idxs.set(i, idxs.get(j));
						idxs.set(j, tmp);
					}
			for (int i = 0; i < n; i++) {
				int idx = idxs.get(i);
				sidxs.add(k);
				gidxs.add(idx);
				String genPath = candidateDir + "/gen" + idx + ".po";
				try {
					FileInputStream fis = new FileInputStream(genPath);
					ObjectInputStream ois = new ObjectInputStream(fis);
					transforms.add((CodeTransform)ois.readObject());
					ois.close();
				}
				catch (Exception e) {
					e.printStackTrace();
					s.close();
					throw new GenesisException("Unable to serialize the generator file: " + genPath);
				}
			}
			s.close();
		}
	}
	
	public SearchSpace(List<CodeTransform> trans) {
		this();
		paths.add("");
		dirs.add("");
		int n = trans.size();
		transforms = new ArrayList<CodeTransform>(trans);
		for (int i = 0; i < n; i++) {
			sidxs.add(0);
			gidxs.add(i);
		}
	}
	
	public static SearchSpace createPARSearchSpace() {
		ArrayList<CodeTransform> ret = new ArrayList<CodeTransform>();
		// Put all manual PAR templates here
		ret.add(new PARNullChecker1().build());
		ret.add(new PARNullChecker2().build());
		ret.add(new PAROffByOne1().build());
		ret.add(new PAROffByOne2().build());
		ret.add(new PARCollectionChecker1().build());
		ret.add(new PARCollectionChecker2().build());
		ret.add(new PARRangeChecker1().build());
		ret.add(new PARRangeChecker2().build());
		ret.add(new PARLowerBoundSet().build());
		ret.add(new PARUpperBoundSet().build());
		ret.add(new PARCastChecker1().build());
		ret.add(new PARCastChecker2().build());
		ret.add(new PARCasterMutateVariable().build());
		ret.add(new PARCasterMutateCall().build());
		return new SearchSpace(ret);
	}
	
	public static SearchSpace createPARNPESearchSpace() {
		ArrayList<CodeTransform> ret = new ArrayList<CodeTransform>();
		ret.add(new PARNullChecker1().build());
		ret.add(new PARNullChecker2().build());
		return new SearchSpace(ret);
	}
	
	public static SearchSpace createPAROOBSearchSpace() {
		ArrayList<CodeTransform> ret = new ArrayList<CodeTransform>();
		ret.add(new PAROffByOne1().build());
		ret.add(new PAROffByOne2().build());
		ret.add(new PARCollectionChecker1().build());
		ret.add(new PARCollectionChecker2().build());
		ret.add(new PARRangeChecker1().build());
		ret.add(new PARRangeChecker2().build());
		ret.add(new PARLowerBoundSet().build());
		ret.add(new PARUpperBoundSet().build());
		return new SearchSpace(ret);
	}
	
	public static SearchSpace createPARCCESearchSpace() {
		ArrayList<CodeTransform> ret = new ArrayList<CodeTransform>();
		ret.add(new PARCastChecker1().build());
		ret.add(new PARCastChecker2().build());
		ret.add(new PARCasterMutateVariable().build());
		ret.add(new PARCasterMutateCall().build());
		return new SearchSpace(ret);
	}
	
	public int numTransforms() {
		return transforms.size();
	}
	
	@Override
	public String toString() {
		int n = transforms.size();
		StringBuffer ret = new StringBuffer();
		for (int i = 0; i < n; i++) {
			ret.append("Generator: " + i +"\n");
			ret.append("Original sidx: " + sidxs.get(i) + "\n");
			ret.append("Original idx: " + gidxs.get(i) + "\n");
			ret.append(transforms.get(i).toString() + "\n");
		}
		return ret.toString();
	}
	
	public static class GenerationResult {
		public int sidx;
		public int gidx;
		public CodeTransform transform;
		public MyCtNode patch;
		
		public GenerationResult() {
			sidx = 0;
			gidx = 0;
			transform = null;
			patch = null;
		}
		
		public GenerationResult(int sidx, int gidx, CodeTransform transform, MyCtNode patch) {
			this.sidx = sidx;
			this.gidx = gidx;
			this.transform = transform;
			this.patch = patch;
		}
	}
	
	public List<GenerationResult> applyTo(Set<MyNodeSig> inside, MyCtNode before) {
		ArrayList<GenerationResult> ret = new ArrayList<GenerationResult>();
		//int cnt = 0;
		int n = transforms.size();
		for (int genIdx = 0; genIdx < n; genIdx++) {
			//cnt ++;
			//if (cnt != 4) continue;
			CodeTransform gen = transforms.get(genIdx);
			CodeTransAdapter adapter = new CodeTransAdapter(gen, before.getFactory());
			if (adapter.checkInside(inside)) {
				if (adapter.applyTo(before)) {
					int m = (int) adapter.prepareGenerate();
					for (int i = 0; i < m; i++) {
						MyCtNode patch = adapter.generateOne();
						if (patch != null && adapter.passTypecheck()) {
							ret.add(new GenerationResult(sidxs.get(genIdx), gidxs.get(genIdx), gen, patch));
						}
						else
							failedCheckCnt ++;
					}
				}
			}
		}
		return ret;
	}
	
	public int getFailedCheckCnt() {
		return failedCheckCnt;
	}
	
	public static void main(String args[]) {
		if (args.length == 1 && args[0].toLowerCase().equals("par")) {
			// A special case to print par templates
			SearchSpace parSpace = createPARSearchSpace();
			System.out.println("Number of Transforms: " + parSpace.numTransforms());
			System.out.println(parSpace.toString());
		}
		else {
			if (args.length < 2) {
				System.out.println("Main <SearchSpaceFile> <CandidateGeneratorDir>");
				System.exit(1);
			}
			String[] a = new String[1];
			String[] b = new String[1];
			a[0] = args[0]; b[0] = args[1];
			SearchSpace space = new SearchSpace(a, b);
			System.out.println("Number of Transforms: " + space.numTransforms());
			System.out.println(space.toString());
		}
	}
}
