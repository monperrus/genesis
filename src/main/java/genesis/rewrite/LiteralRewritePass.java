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
package genesis.rewrite;

import genesis.node.MyCtNode;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtLiteral;

public class LiteralRewritePass implements CodePairRewritePass {

	MyCtNode resBefore;
	MyCtNode resAfter;
	
	public LiteralRewritePass() {
		resBefore = null;
		resAfter = null;
	}
	
	@Override
	public boolean run(MyCtNode before, MyCtNode after) {
		resBefore = before;
		resAfter = after;
		if (!before.isEleClass(CtLiteral.class) || !after.isEleClass(CtLiteral.class))
			return false;
		CtLiteral<?> l1, l2;
		l1 = (CtLiteral<?>) before.getRawObject();
		l2 = (CtLiteral<?>) after.getRawObject();
		if (l1.getValue() == null || l2.getValue() == null)
			return false;
		if (!(l1.getValue() instanceof Integer) || !(l2.getValue() instanceof Integer))
			return false;
		Integer v1, v2;
		v1 = (Integer) l1.getValue();
		v2 = (Integer) l2.getValue();
		if (v1 + 1 == v2) {
			CtBinaryOperator<Integer> binop = after.getFactory().Core().createBinaryOperator();
			CtLiteral<Integer> rightOP = after.getFactory().Core().createLiteral();
			CtLiteral<Integer> leftOP = after.getFactory().Core().createLiteral();
			leftOP.setValue(v1);
			rightOP.setValue(1);
			binop.setLeftHandOperand(leftOP);
			binop.setRightHandOperand(rightOP);
			binop.setKind(BinaryOperatorKind.PLUS);
			binop.setParent(l2.getParent());
			resAfter = new MyCtNode(binop, false);
			return true;
		}
		else
			return false;
	}

	@Override
	public MyCtNode getResultBefore() {
		return resBefore;
	}

	@Override
	public MyCtNode getResultAfter() {
		return resAfter;
	}

}
