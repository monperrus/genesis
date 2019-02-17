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
import java.util.Map;

import genesis.node.MyNodeSig;

public class TransASTCtEle extends TransASTNode implements Serializable {
	
	// This is just for serialization
	protected TransASTCtEle() {
		children = null;
	}
	
	TransASTCtEle(MyNodeSig sig, Map<String, TransASTNode> children) {
		super(sig);
		this.children = children;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Map<String, TransASTNode> children;
	
	@Override
	public boolean treeEquals(TransASTNode a) {
		if (!(a instanceof TransASTCtEle))
			return false;
		TransASTCtEle c = (TransASTCtEle) a;
		if (!c.nodeSig.equals(nodeSig)) return false;
		if (children.size() != c.children.size()) return false;
		for (String name : children.keySet()) {
			if (!c.children.containsKey(name)) return false;
			if (!children.get(name).treeEquals(c.children.get(name))) return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "ele<" + nodeSig.toString() + "," + children.toString() + ">";
	}
}
