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
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;

public class CodeStyleNormalizationPass implements CodeRewritePass {
	
	MyCtNode res;
	
	public CodeStyleNormalizationPass() { 
		this.res= null;
	}
	
	class CodeStyleScanner extends CtScanner {
		boolean changed;
		
		public CodeStyleScanner() {
			changed = false;
		}
		
		@Override
		public void exit(CtElement ele) {
			Factory f = ele.getFactory();
			if (ele instanceof CtLoop) {
				CtStatement body = ((CtLoop) ele).getBody();
				if (!(body instanceof CtBlock)) {
					changed = true;
					CtBlock<?> newBody = f.Core().createBlock();
					newBody.addStatement(body);
					body.setParent(newBody);
					newBody.setParent(ele);
					((CtLoop) ele).setBody(newBody);
				}
			}
			else if (ele instanceof CtIf) {
				CtStatement thenBody = ((CtIf) ele).getThenStatement();
				CtStatement elseBody = ((CtIf) ele).getElseStatement();
				if (thenBody != null) {
					if (!(thenBody instanceof CtBlock)) {
						changed = true;
						CtBlock<?> newBody = f.Core().createBlock();
						newBody.addStatement(thenBody);
						thenBody.setParent(newBody);
						newBody.setParent(ele);
						((CtIf) ele).setThenStatement(newBody);
					}
				}
				if (elseBody != null) {
					if (!(elseBody instanceof CtBlock)) {
						changed = true;
						CtBlock<?> newBody = f.Core().createBlock();
						newBody.addStatement(elseBody);
						elseBody.setParent(newBody);
						newBody.setParent(ele);
						((CtIf) ele).setElseStatement(newBody);
					}
				}
			}
			else if (ele instanceof CtBinaryOperator) {
				CtBinaryOperator<?> binop = (CtBinaryOperator<?>) ele;
				// change the order of the two if possible
				if (binop.getKind() == BinaryOperatorKind.EQ || binop.getKind() == BinaryOperatorKind.NE) {
					CtExpression<?> lop = binop.getLeftHandOperand();
					CtExpression<?> rop = binop.getRightHandOperand();
					if (lop instanceof CtLiteral && !(rop instanceof CtLiteral)) {
						changed = true;
						binop.setLeftHandOperand(rop);
						binop.setRightHandOperand(lop);
					}
				}
			}
			/*else if (ele instanceof CtLocalVariable) {
				if (((CtLocalVariable<?>) ele).getModifiers().contains(ModifierKind.FINAL)) {
					changed = true;
					((CtLocalVariable<?>) ele).removeModifier(ModifierKind.FINAL);
				}
			}*/
		}
	}
	
	@Override
	public boolean run(MyCtNode tree) {
		if (tree.isTrait()) {
			res = tree;
			return false;
		}
		CodeStyleScanner scanner1 = new CodeStyleScanner();
		res = tree.deepClone();
		res.acceptScanner(scanner1);
		if (scanner1.changed) {
			res.setParent((CtElement) tree.parentEleNode().getRawObject());
		}
		else
			res = tree;
		return scanner1.changed;
	}

	@Override
	public MyCtNode getResult() {
		return res;
	}
}
