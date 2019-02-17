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

import java.util.ArrayList;
import java.util.HashSet;

import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtReference;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.code.CtStatementImpl;
import spoon.support.reflect.declaration.CtConstructorImpl;
import spoon.support.reflect.declaration.CtMethodImpl;
import spoon.support.reflect.declaration.CtPackageImpl;

public class CodePairDecomposer {

	MyCtNode before;
	MyCtNode after;
	
	public CodePairDecomposer(MyCtNode before, MyCtNode after) {
		this.before = before;
		this.after = after;
	}

	// Test whether before can go to after if all t1 is transformed to t2
	private boolean validate(MyCtNode before, MyCtNode after, MyCtNode t1, MyCtNode t2) {
		if (before.treeEquals(after))
			return true;
		if (before.isCollection()) {
			if (before.treeEquals(t1) && after.treeEquals(t2))
				return true;
			if (!after.isCollection()) return false;
			int na = before.getNumChildren();
			int nb = after.getNumChildren();
			if (na == nb) {
				boolean pass = true;
				for (int i = 0; i < na; i++)
					if (!validate(before.getChild(i), after.getChild(i), t1, t2)) {
						pass = false;
						break;
					}
				if (pass) return true;
			}
			// Now more tricky case for collections
			// We are going to just match part of it
			if (!t1.isCollection() || !t2.isCollection()) return false;
			int nt1 = t1.getNumChildren();
			int nt2 = t2.getNumChildren();
			if (na - nt1 + nt2 != nb) return false;
			// OK we are going to assume just one replacement here
			int i0 = 0;
			while (i0 < na && i0 < nb && before.getChild(i0).treeEquals(after.getChild(i0))) i0++;
			int i1 = 0;
			while (i1 < na && i1 < nb && before.getChild(na - 1 - i1).treeEquals(after.getChild(nb - 1 - i1))) i1 ++;
			int l = na - i0 - i1;
			if (l > nt1) return false;
			int si = i0 - (nt1 - l);
			if (si < 0) si = 0;
			for (int i = si; i <= i0; i++) {
				if (i + nt1 > na) break;
				if (i + nt2 > nb) break;
				boolean match = true;
				for (int j = 0; j < nt1; j++)
					if (!before.getChild(i + j).treeEquals(t1.getChild(j))) {
						match = false;
						break;
					}
				if (match) {
					for (int j = 0; j < nt2; j++)
						if (!t2.getChild(j).treeEquals(after.getChild(i + j))) {
							match = false;
							break;
						}
				}
				if (match) return true;
			}
			return false;
		}
		else {
			if (before.treeEquals(t1) && after.treeEquals(t2))
				return true;
			if (!before.nodeEquals(after))
				return false;
			int n = before.getNumChildren();
			for (int i = 0; i < n ; i ++)
				if (!before.getChildName(i).equals("type") && !validate(before.getChild(i), after.getChild(i), t1, t2))
					return false;
			return true;
		}
	}
	
	public ArrayList<DecomposedCodePair> decompose() {
		TreeDiffer diff = new TreeDiffer(before, after);
		ArrayList<DecomposedCodePair> ret = 
				new ArrayList<DecomposedCodePair>();
		MyCtNode t1, t2;
		boolean pass = false;
		HashSet<MyNodeSig> insides = new HashSet<MyNodeSig>();
		do {
			t1 = diff.getT1();
			t2 = diff.getT2();
			if (t1.isNull() || t2.isNull())
				break;
			// We are not going to break things into reference level,
			// Reference is too low level for a code pair.
			// Beacuse reference can be used multiple times, and AST tree
			// does not have the notion of parent for a reference
			if (t1.isReference() || t2.isReference())
				break;
			if (t1.isCollection() && t1.isColClass(CtReference.class))
				break;
			if (t2.isCollection() && t2.isColClass(CtReference.class))
				break;
			HashSet<MyNodeSig> s1 = t1.nodeSigSet();
			HashSet<MyNodeSig> s2 = t2.nodeSigSet();
			if (!s1.contains(new MyNodeSig(CtMethodImpl.class, false)) && !s1.contains(new MyNodeSig(CtConstructorImpl.class, false)) && 
					!s1.contains(new MyNodeSig(CtPackageImpl.class, false)))
				if (!s2.contains(new MyNodeSig(CtMethodImpl.class, false)) && !s2.contains(new MyNodeSig(CtConstructorImpl.class, false)) && 
						!s2.contains(new MyNodeSig(CtPackageImpl.class, false)))
					pass = true;
			// For now we only consider statement or expressions
			if ((t1.isEleClass(CtStatement.class) || t1.isColClass(CtStatement.class)) &&
				(t2.isEleClass(CtStatement.class) || t2.isColClass(CtStatement.class)))
				if (t1.isEleClass(CtExpressionImpl.class) || t1.isColClass(CtExpressionImpl.class)
					|| (t1.isEleClass(CtStatementImpl.class) || t1.isColClass(CtStatementImpl.class)))
					if (t2.isEleClass(CtExpressionImpl.class) || t2.isColClass(CtExpressionImpl.class)
						|| (t2.isEleClass(CtStatementImpl.class) || t2.isColClass(CtStatementImpl.class)))
						pass = true;
			if (pass && validate(before, after, t1, t2)) {
				ret.add(new DecomposedCodePair(insides, t1, t2));
			}
			insides.add(t1.nodeSig());
		}
		while (diff.narrowDown(false));
		
		return ret;
	}
}
