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
package genesis.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import genesis.Config;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.code.CtExpressionImpl;

public class SchemaAbstractor {

	private ArrayList<HashSet<MyNodeSig>> insides;
	private ArrayList<MyCtNode> befores;
	private ArrayList<MyCtNode> afters;
	private ArrayList<HashMap<Integer, MyCtNode>> varBindings;

	public SchemaAbstractor() {
		insides = new ArrayList<HashSet<MyNodeSig>>();
		befores = new ArrayList<MyCtNode>();
		afters = new ArrayList<MyCtNode>();
	}

	public void addMapping(Set<MyNodeSig> inside, MyCtNode before, MyCtNode after) {
		insides.add(new HashSet<MyNodeSig>(inside));
		befores.add(before);
		afters.add(after);
	}

	private TransASTNode dfsConstructPost(TransformSchema schema, ArrayList<MyCtNode> context,
			ArrayList<HashMap<Integer, MyCtNode>> varBindings) {
		Set<Integer> vids = varBindings.get(0).keySet();
		int n = context.size();
		for (Integer vid : vids) {
			boolean ok = true;
			for (int i = 0; i < n; i++) {
				MyCtNode t1 = varBindings.get(i).get(vid);
				MyCtNode t2 = context.get(i);
				if (!t1.treeEquals(t2)) {
					ok = false;
					break;
				}
			}
			// go with the first one that works
			if (ok) {
				return schema.createFreeVar(vid);
			}
		}

		boolean collapsedMsg = false; 
		MyCtNode node0 = context.get(0);
		if (!node0.isReference()) {
			collapsedMsg = Config.collapseTextMsg;
			boolean hasMsg = false;
			boolean ok = true;
			if (collapsedMsg)
				if (!node0.isTextMessageNode()) {
					collapsedMsg = false;
				}
				else
					hasMsg = true;
			for (int i = 1; i < context.size(); i++) {
				if (!context.get(i).nodeEquals(node0)) {
					ok = false;
					break;
				}
				if (collapsedMsg)
					if (!context.get(i).isTextMessageNode()) {
						collapsedMsg = false;
					}
					else
						hasMsg = true;
			}
			collapsedMsg = collapsedMsg && hasMsg;
			// XXX: force the failure if we are going to collapse this node
			if (collapsedMsg) 
				ok = false;
			if (ok) {
				if (node0.isCollection()) {
					ArrayList<TransASTNode> children = new ArrayList<TransASTNode>();
					int m = node0.getNumChildren();
					for (int i = 0; i < m; i++) {
						ArrayList<MyCtNode> newContext = new ArrayList<MyCtNode>();
						newContext.clear();
						for (int j = 0; j < n; j++)
							newContext.add(context.get(j).getChild(i));
						children.add(dfsConstructPost(schema, newContext, varBindings));
					}
					MyNodeSig sig = node0.nodeSig();
					for (int i = 1; i < n; i++)
						sig = sig.getCommonSuperSig(context.get(i).nodeSig());
					return schema.createCollection(sig, children);
				}
				else if (node0.isTrait()) {
					return schema.createTrait(node0.nodeSig(), node0.nodeTrait());
				}
				else {
					HashMap<String, TransASTNode> children = new HashMap<String, TransASTNode>();
					int m = node0.getNumChildren();
					for (int i = 0; i < m; i++) {
						ArrayList<MyCtNode> newContext = new ArrayList<MyCtNode>();
						newContext.clear();
						for (int j = 0; j < n; j++)
							newContext.add(context.get(j).getChild(i));
						children.put(node0.getChildName(i), dfsConstructPost(schema, newContext, varBindings));
					}
					return schema.createCtEle(node0.nodeSig(), children);
				}
			}
		}

		// create a new var
		MyNodeSig sig = node0.nodeSig();
		for (int i = 1; i < n; i++)
			sig = sig.getCommonSuperSig(context.get(i).nodeSig());
		// XXX: Collapsed node get an expression for convenience
		if (collapsedMsg)
			sig = sig.getCommonSuperSig(new MyNodeSig(CtExpressionImpl.class, false));
		TransASTFreeVar newVar = schema.createFreshFreeVar(sig);
		for (int i = 0; i < n; i++)
			varBindings.get(i).put(newVar.vid, context.get(i));
		return newVar;
	}

	private TransASTNode dfsConstructPre(TransformSchema schema,
			ArrayList<MyCtNode> context, ArrayList<HashMap<Integer, MyCtNode>> varBindings) {
		int n = context.size();
		MyCtNode node0 = context.get(0);
		if (!node0.isReference()) {
			boolean ok = true;
			for (int i = 1; i < n; i++) {
				if (!context.get(i).nodeEquals(node0)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				if (node0.isCollection()) {
					int m = node0.getNumChildren();
					ArrayList<TransASTNode> children = new ArrayList<TransASTNode>();
					// the size of a collection should be match
					for (int i = 0; i < m; i++) {
						ArrayList<MyCtNode> newContext = new ArrayList<MyCtNode>();
						newContext.clear();
						for (int j = 0; j < n; j++)
							newContext.add(context.get(j).getChild(i));
						children.add(dfsConstructPre(schema, newContext, varBindings));
					}
					MyNodeSig sig = node0.nodeSig();
					for (int i = 1; i < n; i++)
						sig = sig.getCommonSuperSig(context.get(i).nodeSig());
					return schema.createCollection(sig, children);
				}
				else if (node0.isTrait()) {
					return schema.createTrait(node0.nodeSig(), node0.nodeTrait());
				}
				else {
					int m = node0.getNumChildren();
					HashMap<String, TransASTNode> children = new HashMap<String, TransASTNode>();
					for (int i = 0; i < m; i++) {
						ArrayList<MyCtNode> newContext = new ArrayList<MyCtNode>();
						newContext.clear();
						for (int j = 0; j < n; j++)
							newContext.add(context.get(j).getChild(i));
						children.put(node0.getChildName(i),
								dfsConstructPre(schema, newContext, varBindings));
					}
					return schema.createCtEle(node0.nodeSig(), children);
				}
			}
		}

		// Whether existing varBindings work for this
		Set<Integer> vids = varBindings.get(0).keySet();
		for (Integer vid : vids) {
			boolean ok = true;
			for (int i = 0; i < n; i++)
				if (!context.get(i).treeEquals(varBindings.get(i).get(vid))) {
					ok = false;
					break;
				}
			if (ok)
				return schema.createFreeVar(vid);
		}

		MyNodeSig sig = node0.nodeSig();
		for (int i = 1; i < n; i++)
			sig = sig.getCommonSuperSig(context.get(i).nodeSig());
		TransASTFreeVar newVar = schema.createFreshFreeVar(sig);
		for (int i = 0; i < n; i++)
			varBindings.get(i).put(newVar.vid, context.get(i));
		return newVar;
	}

	public TransformSchema generalize() {
		int n = befores.size();
		TransformSchema schema = new TransformSchema();

		varBindings = new ArrayList<HashMap<Integer, MyCtNode>>();
		varBindings.clear();
		for (int i = 0; i < n; i++)
			varBindings.add(new HashMap<Integer, MyCtNode>());
		ArrayList<MyCtNode> context = befores;
		schema.pre = dfsConstructPre(schema, context, varBindings);

		context = afters;
		schema.post = dfsConstructPost(schema, context, varBindings);

		schema.inside = MyNodeSig.canonicalSigSet(insides.get(0));
		for (int i = 1; i < n; i++)
			schema.inside.retainAll(MyNodeSig.canonicalSigSet(insides.get(i)));
		Iterator<MyNodeSig> it = schema.inside.iterator();
		while (it.hasNext()) {
			MyNodeSig sig = it.next();
			if (sig.isCollection())
				it.remove();
			else if (!sig.isCodeElement())
				it.remove();
		}

		schema.varContains = new HashMap<Integer, Set<MyNodeSig>>();
		HashSet<Integer> vInPre = schema.varsInPre();
		for (HashMap<Integer, MyCtNode> b : varBindings) {
			for (Integer vid : vInPre) {
				if (schema.varContains.containsKey(vid)) {
					schema.varContains.get(vid).retainAll(MyNodeSig.canonicalSigSet(b.get(vid).nodeSigSet()));
				}
				else
					schema.varContains.put(vid, MyNodeSig.canonicalSigSet(b.get(vid).nodeSigSet()));
			}
		}
		for (Integer vid : vInPre) {
			it = schema.varContains.get(vid).iterator();
			while (it.hasNext()) {
				MyNodeSig sig = it.next();
				if (!sig.isCodeElement())
					it.remove();
			}
		}
		
		return schema;
	}

	/** Return the varBindings from the last generalize() call **/
	public ArrayList<HashMap<Integer, MyCtNode>> getVarBindings() {
		return varBindings;
	}

	public ArrayList<VarTypeContext> getVarTypeContexts(TransformSchema schema, Integer vid) {
		ArrayList<VarTypeContext> ret = new ArrayList<VarTypeContext>();
		for (int i = 0; i < befores.size(); i ++) {
			HashMap<Integer, MyCtNode> vb = varBindings.get(i);
			Factory f = befores.get(i).getFactory();
			if (f != null)
				ret.add(schema.getVarTypeContext(f, vid, vb));
			else
				ret.add(new VarTypeContext());
		}
		return ret;
	}
}