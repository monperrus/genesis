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
import java.util.Set;

import genesis.Config;
import genesis.generator.EnumerateGenerator;
import genesis.generator.VarGenerator;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.SchemaAdapter;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.declaration.CtClassImpl;

public class CodeTransAdapter {

	CodeTransform gen;
	Factory spoonf;
	SchemaAdapter adapter;
	MyCtNode root;

	HashMap<Integer, ArrayList<MyCtNode>> genCache;
	ArrayList<Integer> cursors, vids;

	public CodeTransAdapter(CodeTransform gen, Factory f) {
		this.spoonf = f;
		this.gen = gen;
		adapter = new SchemaAdapter(f, gen.schema);
		root = null;
		this.genCache = null;
		this.cursors = null;
		this.vids = null;
	}

	public boolean checkInside(Set<MyNodeSig> sigs) {
		return adapter.checkInside(sigs);
	}

	public boolean applyTo(MyCtNode n) {
		root = n;
		// This is an ugly fix for the mismatch of expression/statement
		// in the spoon class tree. They are interfaces with weird 
		// intersections 
		if (n.isColClass(CtClassImpl.class) || n.isEleClass(CtClassImpl.class)) {
			if (!gen.canGenClassNode())
				return false;
		}
		else {
			if (n.isExpressionFieldOnly()) {
				if (gen.canGenStatementNodeOnly())
					return false;
			}
			if (n.isStatementFieldOnly()) {
				if (gen.canGenExpressionNodeOnly())
					return false;
			}
			if (!n.isTrait()) {
				if (gen.canGenTraitOnly()) {
					return false;
				}
			}
		}
		return adapter.applyTo(n);
	}

	public long estimateCost() {
		long ret = 1;
		for (Integer vid : gen.vargens.keySet()) {
			long tmp = gen.vargens.get(vid).estimateCost(root);
			if (tmp < 0) return -1;
			ret *= tmp;
			if (ret > Config.costlimit) return -1;
		}
		return ret;
	}

	public boolean covers(MyCtNode n) {
		HashMap<Integer, MyCtNode> res = adapter.matchWith(n);
		if (res == null) return false;
		for (Integer vid : res.keySet()) {
			if (gen.schema.isInferableTypeVid(vid)) continue;
			if (!gen.vargens.get(vid).covers(root, res.get(vid), adapter.getVarTypeContext(gen.schema, vid))) return false;
		}
		return true;
	}

	public long prepareGenerate(boolean useCloneCache) {
		if (useCloneCache)
			adapter.initCloneCache();
		else
			adapter.disableCloneCache();
		genCache = new HashMap<Integer, ArrayList<MyCtNode>>();
		cursors = new ArrayList<Integer>();
		vids = new ArrayList<Integer>();
		long ret = 1;
		//boolean allowCast = false;
		for (Integer vid : gen.vargens.keySet()) {
			VarGenerator varGen = gen.vargens.get(vid);
			/*if (varGen instanceof EnumerateGenerator) {
				if (((EnumerateGenerator) varGen).getAllowCast())
					allowCast = true;
			}*/
			genCache.put(vid, varGen.generate(root, spoonf, adapter.getVarTypeContext(gen.schema, vid)));
			vids.add(vid);
			cursors.add(0);
			ret *= genCache.get(vid).size();
		}
		//adapter.setAllowCast(allowCast);
		adapter.setAllowCast(true);

		return ret;
	}
	
	public long prepareGenerate() {
		return prepareGenerate(true);
	}

	public MyCtNode generateOne() {
		adapter.clearAllPostVarBinding();
		for (int i = 0; i < cursors.size(); i++) {
			adapter.setVarBinding(vids.get(i), genCache.get(vids.get(i)).get(cursors.get(i)));
		}

		int i = cursors.size() - 1;
		boolean done = false;
		while (i >= 0 && !done) {
			int v = cursors.get(i) + 1;
			if (v >= genCache.get(vids.get(i)).size())
				v = 0;
			else {
				done = true;
			}
			cursors.set(i, v);
			i = i - 1;
		}

		return adapter.getTransformedTree();
	}

	public boolean passTypecheck() {
		return adapter.passTypecheck();
	}
}
