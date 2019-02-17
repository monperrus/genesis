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
package genesis.analysis;

import java.util.ArrayList;
import java.util.List;

import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.visitor.CtScanner;

public class StatementAnalyzer {

	MyCtNode tree;
	MyCtNode parentFunc;
	MyCtNode parentPackage;
	
	public StatementAnalyzer(MyCtNode tree) {
		this.tree = tree;
		MyCtNode curNode = tree.parentEleNode();
		while (curNode != null) {
			if (curNode.isEleClass(CtMethod.class) || curNode.isEleClass(CtConstructor.class) || curNode.isEleClass(CtPackage.class)) break;
			curNode = curNode.parentEleNode();
		}
		if (curNode.isEleClass(CtPackage.class))
			this.parentFunc = tree;
		else
			this.parentFunc = curNode;
		while (curNode != null) {
			if (curNode.isEleClass(CtPackage.class)) break;
			curNode = curNode.parentEleNode();
		}
		this.parentPackage = curNode;
	}

	class StatementScanner extends CtScanner {
		
		ArrayList<MyCtNode> ret;
		MyNodeSig rootSig;
		int maxLen;
		
		StatementScanner(MyNodeSig rootSig, int maxLen) {
			this.rootSig = rootSig;
			this.maxLen = maxLen;
			ret = new ArrayList<MyCtNode>();
		}
		
		@Override
		public void enter(CtElement ele) {
			if (ele instanceof CtStatementList) {
				CtStatementList ctl = (CtStatementList) ele;
				List<CtStatement> l = ctl.getStatements();
				for (int i = 0; i < l.size(); i++) {
					if (rootSig.isCollection() || rootSig.equals(new MyNodeSig(Object.class, false))) {
						int e = maxLen;
						if (i + maxLen > l.size())
							e = l.size() - i;
						for (int j = i + 1; j <= i + e; j++) {
							boolean ok = true;
							for (int k = i; k < j; k++) {
								if (!rootSig.getClassSig().isAssignableFrom(l.get(k).getClass())) {
									ok = false;
									break;
								}
								// XXX: We are not going to copy any LocalVariable declaration at the top level, 
								// this does not help at all, only causing non-compilable patches
								if (l.get(k) instanceof CtLocalVariable) {
									ok = false;
									break;
								}
							}
							if (!ok) continue;
							ArrayList<CtStatement> res = new ArrayList<CtStatement>();
							for (int k = i; k < j; k++)
								res.add(l.get(k));
							ret.add(new MyCtNode(res, false));
						}
					}
					// XXX: Not allowing LocalVariable declaration in the top level!
					if (!(l.get(i) instanceof CtLocalVariable)) {
						if ((!rootSig.isCollection() && rootSig.isAssignableFrom(l.get(i).getClass())) || 
								(rootSig.equals(new MyCtNode(Object.class, false)))) {
							ret.add(new MyCtNode(l.get(i), false));
						}
					}
				}
			}
		}
		
		ArrayList<MyCtNode> getResult() {
			return ret;
		}
	}
	
	public ArrayList<MyCtNode> getCandidateInFunc(MyNodeSig rootSig, int maxLen) {
		if (parentFunc == null) return new ArrayList<MyCtNode>();
		StatementScanner s = new StatementScanner(rootSig, maxLen);
		parentFunc.acceptScanner(s);
		return s.getResult();
	}

	public ArrayList<MyCtNode> getCandidateInPackage(MyNodeSig rootSig, int maxLen) {
		if (parentPackage == null) return new ArrayList<MyCtNode>();
		StatementScanner s = new StatementScanner(rootSig, maxLen);
		parentPackage.acceptScanner(s);
		return s.getResult();
	}
}
