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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import genesis.GenesisException;
import genesis.analysis.StaticAnalyzer;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.util.RtHelper;

public class VarDeclEliminationPass implements CodePairRewritePass {

	MyCtNode resBefore;
	MyCtNode resAfter;
	
	public VarDeclEliminationPass() {
		resBefore = null;
		resAfter = null;
	}
	
	class VarDeclReplacer extends CtScanner {
		
		boolean failed;
		String varName;
		CtExpression<?> initExpr;
		
		VarDeclReplacer(CtLocalVariable<?> var, CtExpression<?> initExpr) {
			failed = false;
			this.varName = var.getSimpleName();
			this.initExpr = initExpr;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public void exit(CtElement ele) {
			if (failed) return;
			if (ele instanceof CtVariableWrite) {
				CtVariableReference<?> ref = ((CtVariableWrite<?>) ele).getVariable();
				if (ref instanceof CtLocalVariableReference)
					if (ref.toString().equals(varName)) {
						failed = true;
						return;
					}
			}
			else if (ele instanceof CtVariableRead) {
				// ++ and -- might be VariableRead in spoon,
				// Yeah I really hate you spoon
				CtElement pele = ele.getParent();
				if (pele != null)
					if (pele instanceof CtUnaryOperator) {
						UnaryOperatorKind k = ((CtUnaryOperator<?>) pele).getKind();
						if (k == UnaryOperatorKind.POSTDEC || k == UnaryOperatorKind.PREDEC || k == UnaryOperatorKind.POSTINC || k == UnaryOperatorKind.PREINC) {
							failed = true;
							return;
						}
					}
				CtVariableReference<?> ref = ((CtVariableRead<?>) ele).getVariable();
				if (ref instanceof CtLocalVariableReference)
					if (ref.toString().equals(varName)) {
						CtElement parentEle = ele.getParent();
						try {
							for (Field f : RtHelper.getAllFields(parentEle.getClass())) {
								if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
								f.setAccessible(true);
								Object o = f.get(parentEle);
								if (o instanceof Collection) {
									Collection<Object> c = (Collection<Object>) o;
									boolean found = false;
									for (Object o1 : c) {
										if (o1 == ele) {
											found = true;
											break;
										}
									}
									if (found) {
										ArrayList<Object> tmp = new ArrayList<Object>();
										CtExpression<?> clonedExpr = initExpr.getFactory().Core().clone(initExpr);
										for (Object o1 : c) {
											if (o1 == ele)
												tmp.add(clonedExpr);
											else
												tmp.add(o1);
										}
										f.set(parentEle, tmp);
										clonedExpr.setParent(parentEle);
									}
								}
								else if (o == ele) {
									CtExpression<?> clonedExpr = initExpr.getFactory().Core().clone(initExpr);
									f.set(parentEle, clonedExpr);
									clonedExpr.setParent(parentEle);
								}
							}
						}
						catch (Exception e) {
							e.printStackTrace();
							throw new GenesisException("Failed to replace parent element of: " + ele.toString() + " - " + parentEle.toString());
						}
					}
			}
		}
		
	}
	
	@Override
	public boolean run(MyCtNode before, MyCtNode after) {
		resBefore = before;
		resAfter = after;
		if (!before.isCollection() || !after.isCollection()) 
			return false;
		if (before.getNumChildren() == 0 || after.getNumChildren() == 0)
			return false;
		MyCtNode before1 = before.getChild(0);
		MyCtNode after1 = after.getChild(0);
		if (!after1.isEleClass(CtLocalVariableImpl.class))
			return false;
		CtLocalVariable<?> var = (CtLocalVariable<?>) after1.getRawObject();
		if (var.getDefaultExpression() == null)
			return false;
		if (before1.isEleClass(CtLocalVariableImpl.class)) {
			// We do not do this if the beforeTree is declaring a new var
			CtLocalVariable<?> varb = (CtLocalVariable<?>) before1.getRawObject();
			StaticAnalyzer ana2 = new StaticAnalyzer(after); 
			if (!ana2.inBefore(varb.getReference()))
				return false;
		}
		// We do not do this if this variable is inside the before tree
		StaticAnalyzer ana = new StaticAnalyzer(before);
		if (ana.inBefore(var.getReference())) {
			return false;
		}
		// Now we can try to eliminate this declaration from the fix... just to help fixing stuff
		CtExpression<?> initExpr = var.getDefaultExpression();
		ArrayList<Object> tmp = new ArrayList<Object>();
		for (int i = 1; i < after.getNumChildren(); i++)
			tmp.add(after.getChild(i).deepClone().getRawObject());
		resAfter = new MyCtNode(tmp, false);
		//resAfter = after.deepClone();
		VarDeclReplacer rep = new VarDeclReplacer(var, initExpr);
		resAfter.acceptScanner(rep);
		if (rep.failed) {
			resAfter = after;
			return false;
		}
		else {
			resAfter.setParent((CtElement) after.parentEleNode().getRawObject());
		}
		
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
