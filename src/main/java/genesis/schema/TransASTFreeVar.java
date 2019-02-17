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

import genesis.node.MyNodeSig;

public class TransASTFreeVar extends TransASTNode implements Serializable {
	/**
	 * The ID
	 */
	private static final long serialVersionUID = 1L;

	int vid;

	protected TransASTFreeVar() {
		vid = 0;
	}
	
    TransASTFreeVar(MyNodeSig sig, Integer vid) {
    	super(sig);
		this.vid = vid;
	}
    
    @Override
    public boolean treeEquals(TransASTNode a) {
    	if (!(a instanceof TransASTFreeVar)) return false;
    	TransASTFreeVar v = (TransASTFreeVar) a;
    	if (!nodeSig.equals(v.nodeSig)) return false;
    	return vid == v.vid;
    }
    
    @Override
    public String toString() {
    	return "var<" + nodeSig.toString() + "," + Integer.toString(vid) + ">";
    }
}
