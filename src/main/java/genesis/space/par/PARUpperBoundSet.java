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

import com.google.common.collect.ImmutableSet;

import genesis.generator.EnumerateGenerator;
import genesis.generator.RefBound;
import genesis.generator.ReferenceGenerator;
import genesis.transform.ManualTransformRule;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.code.CtFieldReadImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import spoon.support.reflect.reference.CtVariableReferenceImpl;

public class PARUpperBoundSet extends ManualTransformRule {
	public PARUpperBoundSet() {
		super();
		inside.add(sig(CtCodeElementImpl.class));
		vargens.put(1, new ReferenceGenerator(
					sig(CtVariableReferenceImpl.class),
					ReferenceGenerator.ReferenceScopeKind.NONE,
					ReferenceGenerator.ReferenceScopeKind.BEFORE,
					ReferenceGenerator.ReferenceScopeKind.NONE,
					"",
					false
				));
		vargens.put(2, new EnumerateGenerator(
				sig(CtExpressionImpl.class), // rootSig
				ImmutableSet.of(sig(CtVariableReadImpl.class), sig(CtFieldReadImpl.class), sig(CtInvocationImpl.class)), // allowSigs
				3, // eleBound
				new RefBound(3, 0, 0, 0), // varBound
				new RefBound(3, 0, 0, 0), // execBound
				new RefBound(), // bindTargetBound
				ImmutableSet.of(), // allowedConst
				0, // constBound
				false, // allowCast
				false // allowNull
				));
		varsigs.put(0, sig(CtCodeElementImpl.class));
		varsigs.put(1, sig(CtVariableReferenceImpl.class));
		varsigs.put(2, sig(CtExpressionImpl.class));
		topSig = ManualTransformRule.TopLevelSig.ColCodeEleSig;
	}

	public Object preAST() {
		sugarStart();
		ASTVar(0);
		sugarEnd();
		return null;
	}
	
	private Object[] ASTVarA(int id) {
		return null;
	}
	
	public Object postAST() {
		int ASTVar_1 = 0;
		sugarStart();
		if (ASTVar_1 >= ASTVarA(2).length)
			ASTVar_1 = ASTVarA(2).length - 1;
		ASTVar(0);
		sugarEnd();
		return null;
	}
}