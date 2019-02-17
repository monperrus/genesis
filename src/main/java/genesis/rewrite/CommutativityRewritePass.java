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

public class CommutativityRewritePass implements CodePairRewritePass {

	MyCtNode resBefore;
	MyCtNode resAfter;

	public CommutativityRewritePass() {
		resBefore = null;
		resAfter = null;
	}
	
	@Override
	public boolean run(MyCtNode before, MyCtNode after) {
		resBefore = before;
		resAfter = after;
		if (!after.isEleClass(CtBinaryOperator.class)) {
			return false;
		}
		CtBinaryOperator<?> binop = (CtBinaryOperator<?>) after.getRawObject();
		if (binop.getKind() != BinaryOperatorKind.PLUS)
			return false;
		if (!new MyCtNode(binop.getRightHandOperand(),false).treeEquals(before))
			return false;
		CtBinaryOperator<?> newbop = after.getFactory().Core().clone(binop);
		newbop.setLeftHandOperand(binop.getRightHandOperand());
		newbop.setRightHandOperand(binop.getLeftHandOperand());
		newbop.setParent(binop.getParent());
		resAfter = new MyCtNode(newbop, false);
		return true;
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
