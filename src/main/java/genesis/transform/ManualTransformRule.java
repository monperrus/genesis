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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import genesis.generator.VarGenerator;
import genesis.node.MyNodeSig;
import genesis.schema.TransformSchema;

public abstract class ManualTransformRule {
	
	public static enum TopLevelSig {
		ExprSig,
		ColExprSig,
		CodeEleSig,
		ColCodeEleSig
	}
	
	public boolean dup;
	public Map<Integer, Set<MyNodeSig>> varContains;
	public Set<MyNodeSig> inside;
	public Map<Integer, VarGenerator> vargens;
	public Map<Integer, MyNodeSig> varsigs;
	public TopLevelSig topSig;
	
	public ManualTransformRule() {
		dup = false;
		varContains = new HashMap<Integer, Set<MyNodeSig>>();
		inside = new HashSet<MyNodeSig>();
		vargens = new HashMap<Integer, VarGenerator>();
		varsigs = new HashMap<Integer, MyNodeSig>();
		topSig = TopLevelSig.ColCodeEleSig;
	}
	
	// An inherited class should call ASTVar to denote an expression or statement
	// It is OK to define another function with prefix ASTVar and different
	// return type
	protected Object ASTVar(int id) { 
		// empty body
		return null;
	}
	
	protected Object ASTColVar(int id) {
		// empty body;
		return null;
	}
	
	// An inherited class should define a function named "preAST"
	// public abstract Object preAST();
	
	// An inherited class should define a function named "postAST"
	// public abstract Object postAST();
	
	protected void sugarStart() { }
	protected void sugarEnd() { }
	protected void sugarExprs(Object... a) { }
	
	public CodeTransform build() {
		TransformSchema schema = TransformSchema.buildFromClass(this);
		return new CodeTransform(schema, vargens);
	}

	// Utility functions
	protected MyNodeSig sig(Class<?> a) {
		return new MyNodeSig(a, false);
	}
}
