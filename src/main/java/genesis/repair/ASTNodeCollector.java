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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import genesis.GenesisException;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.utils.Pair;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtCaseImpl;

public class ASTNodeCollector {

	static class ElementCollector extends CtScanner {
		
		HashSet<MyNodeSig> initialInside;
		HashMap<MyNodeSig, Integer> insides;
		ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> res;
		MyCtNode root;
		CtElement disabledIn;
		
		public ElementCollector(Set<MyNodeSig> initialInside, MyCtNode root) {
			this.insides = new HashMap<MyNodeSig, Integer>();
			this.initialInside = new HashSet<MyNodeSig>(initialInside);
			this.res = new ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>>();
			this.disabledIn = null;
			this.root = root;
		}
		
		private HashSet<MyNodeSig> getCurrentInside() {
			HashSet<MyNodeSig> currentInside = new HashSet<MyNodeSig>(initialInside);
			for (Entry<MyNodeSig, Integer> e : insides.entrySet()) {
				if (e.getValue() > 0)
					currentInside.add(e.getKey());
			}
			return currentInside;
		}
		
		@Override
		public void enter(CtElement ele) {
			MyCtNode node = new MyCtNode(ele, false);
			if (disabledIn == null) {
				// XXX: We do not handle annotations at all
				if (ele instanceof CtAnnotation || (!root.equals(node) && (ele instanceof CtStatement) && node.isStatementFieldOnly())) {
					boolean loopStmt = false;
					CtElement re = (CtElement)root.getRawObject();
					if (re instanceof CtFor)
						if (((CtFor) re).getForInit() == ele || (((CtFor) re).getForUpdate() == ele))
							loopStmt = true;
					if (re instanceof CtForEach)
						if (((CtForEach) re).getVariable() == ele)
							loopStmt = true;
					if (!loopStmt)
						disabledIn = ele;
				}
			}
			if (disabledIn != null) return;
			
			HashSet<MyNodeSig> currentInside = getCurrentInside();
			res.add(new Pair<HashSet<MyNodeSig>, MyCtNode>(currentInside, node));
			int v = 0;
			if (insides.containsKey(node.nodeSig()))
				v = insides.get(node.nodeSig());
			insides.put(node.nodeSig(), v + 1);
			// We also collect child collection because it is not counted
			int n = node.getNumChildren();
			for (int i = 0; i < n; i++) {
				MyCtNode child = node.getChild(i);
				if (child.isCollection()) {
					if (child.getNumChildren() != 0)
						res.add(new Pair<HashSet<MyNodeSig>, MyCtNode>(getCurrentInside(), child));
				}
			}
		}
		
		@Override
		public void exit(CtElement ele) {
			if (disabledIn != null) {
				if (disabledIn == ele)
					disabledIn = null;
				return;
			}
			//if (ele instanceof CtAnnotation) return;
			MyNodeSig sig = new MyNodeSig(ele, false);
			assert(insides.containsKey(sig));
			insides.put(sig, insides.get(sig) - 1);
		}
	};
	
	private static ArrayList<MyCtNode> obtainCandidateColStatement(MyCtNode parentNode, MyCtNode stmtNode) {
		List<CtStatement> stmtList = null;
		if (parentNode.getRawObject() instanceof CtBlockImpl) {
			stmtList = ((CtBlockImpl<?>)parentNode.getRawObject()).getStatements();
		}
		else if (parentNode.getRawObject() instanceof CtCaseImpl) {
			stmtList = ((CtCaseImpl<?>)parentNode.getRawObject()).getStatements();
		}
		else
			throw new GenesisException("Do not know how to handle StatementList: " + parentNode.toString());
		int idx = -1;
		for (int i = 0; i < stmtList.size(); i++)
			if (stmtList.get(i) == stmtNode.getRawObject()) {
				idx = i;
				break;
			}
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		// XXX: This case only happens when the if statement is some branch of a compound statement
		// Let's just ignore it
		if (idx == -1)
			return ret;
		for (int i = idx; i < stmtList.size(); i++) {
			ArrayList<CtStatement> tmp = new ArrayList<CtStatement>();
			for (int j = idx; j <= i; j++)
				tmp.add(stmtList.get(j));
			ret.add(new MyCtNode(tmp, false, parentNode.getRawObject()));
		}
		return ret;
	}
	
	public static ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> getCandidateNodes(Set<MyNodeSig> insides, MyCtNode n) {
		MyCtNode parentNode = n.parentEleNode();
		ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>> ret = new ArrayList<Pair<HashSet<MyNodeSig>, MyCtNode>>();
		if (parentNode.isEleClass(CtStatementList.class)) {	
			ArrayList<MyCtNode> colRet = obtainCandidateColStatement(parentNode, n);
			for (MyCtNode node : colRet) {
				ret.add(new Pair<HashSet<MyNodeSig>, MyCtNode>(new HashSet<MyNodeSig>(insides), node));
			}
		}
	
		ElementCollector collec = new ElementCollector(insides, n);
		n.acceptScanner(collec);
		ret.addAll(collec.res);		
		return ret;
	}
}
