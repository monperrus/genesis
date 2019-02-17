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
import genesis.transform.ManualTransformRule;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.reference.CtVariableReferenceImpl;

public class PARLowerBoundSet extends ManualTransformRule {
	public PARLowerBoundSet() {
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
		varsigs.put(0, sig(CtCodeElementImpl.class));
		varsigs.put(1, sig(CtVariableReferenceImpl.class));
		topSig = ManualTransformRule.TopLevelSig.ColCodeEleSig;
	}

	public Object preAST() {
		sugarStart();
		ASTVar(0);
		sugarEnd();
		return null;
	}
	
	public Object postAST() {
		int ASTVar_1 = 0;
		sugarStart();
		if (ASTVar_1 < 0)
			ASTVar_1 = 0;
		ASTVar(0);
		sugarEnd();
		return null;
	}
}