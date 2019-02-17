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

import java.util.Stack;

public abstract class TransASTVisitor {
	
	protected Stack<TransASTNode> visitStack;
	
	protected TransASTVisitor() { 
		visitStack = new Stack<TransASTNode>();
	}
	
	public boolean visitCollection(TransASTCollection n) { return true; }
	
	public boolean visitCtEle(TransASTCtEle n) { return true; }
	
	public boolean visitFreeVar(TransASTFreeVar n) { return true; }
	
	public boolean visitTrait(TransASTTrait n) { return true; }
	
	public boolean visitNode(TransASTNode n) { return true; }
	
	public boolean scanNode(TransASTNode n) {
		visitStack.push(n);
		boolean ret = true;
		if (n instanceof TransASTCollection)
			ret = this.scanCollection((TransASTCollection)n);
		else if (n instanceof TransASTCtEle)
			ret = this.scanCtEle((TransASTCtEle) n);
		else if (n instanceof TransASTFreeVar)
			ret = this.scanFreeVar((TransASTFreeVar) n);
		else if (n instanceof TransASTTrait)
			ret = this.scanTrait((TransASTTrait) n);
		else
			assert(false);
		visitStack.pop();
		return ret;
	}
	
	public boolean scanTrait(TransASTTrait n) {
		return this.visitTrait(n) && this.visitNode(n);
	}
	
	public boolean scanFreeVar(TransASTFreeVar n) {
		return this.visitFreeVar(n) && this.visitNode(n);
	}
	
	public boolean scanCtEle(TransASTCtEle n) {
		if (!(this.visitCtEle(n) && this.visitNode(n)))
			return false;
		for (String name : n.children.keySet()) {
			if (!this.scanNode(n.children.get(name)))
				return false;
		}
		return true;
	}
	
	public boolean scanCollection(TransASTCollection n) {
		if (!(this.visitCollection(n) && this.visitNode(n)))
			return false;
		for (TransASTNode child : n.children) {
			if (!this.scanNode(child))
				return false;
		}
		return true;
	}
}
