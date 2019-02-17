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
package genesis.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import genesis.generator.CopyGenerator;
import genesis.generator.EnumerateGenerator;
import genesis.generator.ReferenceGenerator;
import genesis.generator.TraitGenerator;
import genesis.generator.VarGenerator;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.SchemaAbstractor;
import genesis.schema.TransformSchema;
import genesis.schema.VarTypeContext;

public class CodeTransAbstractor {
	private SchemaAbstractor abstractor;
	private HashMap<Integer, ArrayList<VarGenerator> > genM;
	private ArrayList<MyCtNode> befores;
	private ArrayList<MyCtNode> afters;

	private TransformSchema schema;
	private ArrayList<CodeTransform> generators;

	public CodeTransAbstractor() {
		this.abstractor = new SchemaAbstractor();
		this.genM = null;
		this.befores = new ArrayList<MyCtNode>();
		this.afters = new ArrayList<MyCtNode>();
		this.schema = null;
		this.generators = null;
	}

	public void addMapping(Set<MyNodeSig> inside, MyCtNode before, MyCtNode after) {
		befores.add(before);
		afters.add(after);
		abstractor.addMapping(inside, before, after);
	}

	public boolean generalize() {
		schema = abstractor.generalize();
		ArrayList<HashMap<Integer, MyCtNode>> varBindings = abstractor.getVarBindings();
		assert( varBindings.size() > 0);
		HashSet<Integer> vars = schema.varsInPost();
		genM = new HashMap<Integer, ArrayList<VarGenerator>>();
		for (Integer vid : vars) {
			if (schema.isInferableTypeVid(vid)) continue;
			ArrayList<MyCtNode> trees = new ArrayList<MyCtNode>();
			for (HashMap<Integer, MyCtNode> b : varBindings) {
				trees.add(b.get(vid));
			}
			ArrayList<VarGenerator> gens = new ArrayList<VarGenerator>();
			ArrayList<VarTypeContext> ctxts = abstractor.getVarTypeContexts(schema, vid);
			VarGenerator gen = EnumerateGenerator.createGenerator(trees, befores, ctxts);
			if (gen != null)
				gens.add(gen);
			gens.addAll(CopyGenerator.createGenerators(trees, befores));
			gen = ReferenceGenerator.createGenerator(trees, befores, ctxts);
			if (gen != null)
				gens.add(gen);
			gen = TraitGenerator.createGenerator(trees, befores);
			if (gen != null)
				gens.add(gen);
			if (gens.size() == 0) {
				schema = null;
				genM = null;
				return false;
			}
			else
				genM.put(vid, gens);
		}
		return true;
	}

	public int getNumGenerators() {
		if (genM == null || schema == null)
			return 0;
		int ret = 1;
		for (Integer vid : genM.keySet()) {
			ret *= genM.get(vid).size();
		}
		return ret;
	}

	public CodeTransform getGenerator(int idx) {
		if (generators == null)
			createASTGenerators();
		return generators.get(idx);
	}

	private void recursiveAdd(ArrayList<Integer> vids, HashMap<Integer, ArrayList<VarGenerator>> genM,
			Stack<VarGenerator> stack, ArrayList<CodeTransform> gens) {
		if (stack.size() == vids.size()) {
			HashMap<Integer, VarGenerator> tmpM = new HashMap<Integer, VarGenerator>();
			for (int i = 0; i < stack.size(); i++)
				tmpM.put(vids.get(i), stack.get(i));
			gens.add(new CodeTransform(schema, tmpM));
			return;
		}
		int n = stack.size();
		Integer vid = vids.get(n);
		for (VarGenerator g : genM.get(vid)) {
			stack.push(g);
			recursiveAdd(vids, genM, stack, gens);
			stack.pop();
		}
	}

	private void createASTGenerators() {
		generators = new ArrayList<CodeTransform>();
		ArrayList<Integer> vids = new ArrayList<Integer>(genM.keySet());
		Stack<VarGenerator> stack = new Stack<VarGenerator>();
		recursiveAdd(vids, genM, stack, generators);
	}

	public ArrayList<CodeTransform> getGenerators() {
		if (generators == null)
			createASTGenerators();
		return generators;
	}
}
