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
package genesis.space.par;

import genesis.generator.ReferenceGenerator;
import genesis.node.MyNodeSig;
import genesis.transform.ManualTransformRule;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.reference.CtVariableReferenceImpl;

public class PARCasterMutateCall extends ManualTransformRule {
	public PARCasterMutateCall() {
		super();
		inside.add(sig(CtCodeElementImpl.class));
		vargens.put(2, new ReferenceGenerator(
					sig(CtVariableReferenceImpl.class),
					ReferenceGenerator.ReferenceScopeKind.BINDING,
					ReferenceGenerator.ReferenceScopeKind.NONE,
					ReferenceGenerator.ReferenceScopeKind.NONE,
					"",
					true
				));
		varsigs.put(0, sig(CtVariableReferenceImpl.class));
		varsigs.put(1, new MyNodeSig(CtExpressionImpl.class, true));
		varsigs.put(2, sig(CtVariableReferenceImpl.class));
		topSig = ManualTransformRule.TopLevelSig.ExprSig;
	}

	public Object ASTERefVar_0(Object a) {
		return null;
	}
	
	public Object ASTERefVar_2(Object a) {
		return null;
	}
	
	public Object preAST() {
		sugarExprs(ASTERefVar_0(ASTColVar(1)));
		return null;
	}
	
	public Object postAST() {
		sugarExprs(ASTERefVar_2(ASTColVar(1)));
		return null;
	}
}