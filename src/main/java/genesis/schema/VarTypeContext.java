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

import genesis.schema.TransASTFreeVar;
import genesis.schema.TransASTNode;

import spoon.reflect.reference.CtTypeReference;

// This class holds all relevant type information
// that can be captured in the schema for a free var
public class VarTypeContext {
	// If this free var is the right hand side of a targeted expression,
	// this holds the target type
	public CtTypeReference<?> targetType;
	public boolean constructorOnly;
	public boolean exceptionOnly;

	public TransASTFreeVar n;
	public TransASTNode parent;

	public VarTypeContext() {
		this.targetType = null;
		this.constructorOnly = false;
		this.exceptionOnly = false;
	}
	
	public VarTypeContext(CtTypeReference<?> targetType, boolean constructorOnly, boolean exceptionOnly) {
		this.targetType = targetType;
		this.constructorOnly = constructorOnly;
		this.exceptionOnly = exceptionOnly;
	}
}
