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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import genesis.generator.EnumerateGenerator;
import genesis.generator.RefBound;
import genesis.transform.ManualTransformRule;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.code.CtFieldReadImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import spoon.support.reflect.code.CtReturnImpl;
import spoon.support.reflect.code.CtVariableReadImpl;

public class PARCollectionChecker1 extends ManualTransformRule {
	
	public PARCollectionChecker1() {
		super();
		inside.add(sig(CtCodeElementImpl.class));
		vargens.put(1, new EnumerateGenerator(
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
		vargens.put(3, new EnumerateGenerator(
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
		Set<Object> defaultVal = new HashSet<Object>();
		defaultVal.add(null);
		defaultVal.add(0);
		defaultVal.add(false);
		defaultVal.add("");
		vargens.put(2, new EnumerateGenerator(
				sig(CtCodeElementImpl.class), // rootSig
				ImmutableSet.of(sig(CtLiteralImpl.class), sig(CtReturnImpl.class)), // allowSigs
				2, // eleBound
				new RefBound(), // varBound
				new RefBound(), // execBound
				new RefBound(), // bindTargetBound
				defaultVal, // allowedConst
				1, // constBound
				false, // allowCast
				false // allowNull
				));
		varsigs.put(0, sig(CtCodeElementImpl.class));
		varsigs.put(1, sig(CtExpressionImpl.class));
		varsigs.put(2, sig(CtCodeElementImpl.class));
		varsigs.put(3, sig(CtExpressionImpl.class));
		topSig = ManualTransformRule.TopLevelSig.ColCodeEleSig;
	}

	public Object preAST() {
		sugarStart();
		ASTVar(0);
		sugarEnd();
		return null;
	}
	
	private Collection<?> ASTVarC(int id) { 
		return null;
	}
	
	private int ASTVarI(int id) {
		return 0;
	}
	
	public Object postAST() {
		sugarStart();
		if (ASTVarI(1) >= ASTVarC(3).size())
			ASTVar(2);
		ASTVar(0);
		sugarEnd();
		return null;
	}
}