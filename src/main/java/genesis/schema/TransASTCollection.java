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

import java.io.Serializable;
import java.util.List;

import genesis.node.MyNodeSig;
import spoon.support.reflect.code.CtExpressionImpl;

public class TransASTCollection extends TransASTNode implements Serializable {
	
	// This is only for serialization, should not be used
	protected TransASTCollection() {
		children = null;
	}
	
	TransASTCollection(MyNodeSig sig, List<TransASTNode> children) {
		super(sig);
		this.children = children;
		// XXX: this case may happen if text message collpased
		for (TransASTNode n : children) {
			if (n.nodeSig.equals(new MyNodeSig(CtExpressionImpl.class, false)))
				if (!sig.elementSig().isAssignableFrom(CtExpressionImpl.class)) {
					nodeSig = new MyNodeSig(CtExpressionImpl.class, true);
					break;
				}
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	List<TransASTNode> children;

	public TransASTNode getChild(int n) { return children.get(n); }

	@Override
	public boolean treeEquals(TransASTNode a) {
		if (!(a instanceof TransASTCollection)) return false;
		TransASTCollection c = (TransASTCollection) a;
		if (!nodeSig.equals(c.nodeSig)) return false;
		if (children.size() != c.children.size()) return false;
		int n = children.size();
		for (int i = 0; i < n; i++)
			if (!children.get(i).treeEquals(c.children.get(i)))
				return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "col<" + nodeSig.toString() + "," + children.toString() + ">";
	}
}
