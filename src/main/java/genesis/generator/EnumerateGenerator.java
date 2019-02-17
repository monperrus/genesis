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
package genesis.generator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;

import genesis.Config;
import genesis.analysis.StaticAnalyzer;
import genesis.analysis.TypeHelper;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.VarTypeContext;
import genesis.utils.CombNo;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtArrayWrite;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtOperatorAssignment;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtArrayReadImpl;
import spoon.support.reflect.code.CtArrayWriteImpl;
import spoon.support.reflect.code.CtAssignmentImpl;
import spoon.support.reflect.code.CtBinaryOperatorImpl;
import spoon.support.reflect.code.CtBreakImpl;
import spoon.support.reflect.code.CtCodeSnippetExpressionImpl;
import spoon.support.reflect.code.CtConditionalImpl;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtContinueImpl;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.code.CtFieldReadImpl;
import spoon.support.reflect.code.CtFieldWriteImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import spoon.support.reflect.code.CtOperatorAssignmentImpl;
import spoon.support.reflect.code.CtReturnImpl;
import spoon.support.reflect.code.CtSuperAccessImpl;
import spoon.support.reflect.code.CtThisAccessImpl;
import spoon.support.reflect.code.CtThrowImpl;
import spoon.support.reflect.code.CtUnaryOperatorImpl;
import spoon.support.reflect.code.CtVariableAccessImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import spoon.support.reflect.code.CtVariableWriteImpl;
import spoon.support.reflect.declaration.CtPackageImpl;
import spoon.support.reflect.reference.SpoonClassNotFoundException;

public class EnumerateGenerator extends VarGenerator implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	private MyNodeSig rootSig;
	private HashSet<MyNodeSig> allowedSigs;
	private int eleBound;
	private RefBound varBound, execBound, bindTargetBound; /*typeBound,*/
	private HashSet<Object> allowedConst;
	private int constBound;
	private boolean allowCast;
	private boolean allowNull;
	// Whether to allow things referenced in binding only
	//private boolean allowBindingExec;

	protected EnumerateGenerator() {
		this.rootSig = null;
		this.allowedSigs = null;
		this.eleBound = 0;
		//this.typeBound = null;
		this.varBound = null;
		this.execBound = null;
		this.bindTargetBound = null;
		this.allowedConst = null;
		this.constBound = 0;
		this.allowCast = false;
		this.allowNull = false;
	}
	
	public EnumerateGenerator(MyNodeSig rootSig, Set<MyNodeSig> allowedSigs,
			int eleBound, RefBound varBound, RefBound execBound, RefBound bindTargetBound,
			Set<Object> allowedConst, int constBound, boolean allowCast, boolean allowNull) {
		this.rootSig = rootSig;
		this.allowedSigs = new HashSet<MyNodeSig>(allowedSigs);
		this.eleBound = eleBound;
		this.varBound = new RefBound(varBound);
		this.execBound = new RefBound(execBound);
		this.bindTargetBound = new RefBound(bindTargetBound);
		this.allowedConst = new HashSet<Object>(allowedConst);
		this.constBound = constBound;
		this.allowCast = allowCast;
		this.allowNull = allowNull;
	}

	// These are several functions that should only be used for testing
	/*
	public void setRootSig(MyNodeSig sig) {
		rootSig = sig;
	}

	public void setAllowedSigs(Set<MyNodeSig> sigs) {
		allowedSigs = new HashSet<MyNodeSig>(sigs);
	}

	public void setEleBound(int eleBound) {
		this.eleBound = eleBound;
	}

	public void setVarBound(int inTree, int inFunc, int all) {
		this.varBound = new RefBound(inTree, inFunc, all);
	}

	public void setExecBound(int inTree, int inFunc, int all) {
		this.execBound = new RefBound(inTree, inFunc, all);
	}

	public void setAllowedConst(Set<Object> consts) {
		allowedConst = new HashSet<Object>(consts);
	}

	public void setAllowCast(boolean b) {
		this.allowCast = b;
	}*/

	public EnumerateGenerator(EnumerateGenerator gen) {
		this.rootSig = gen.rootSig;
		this.allowedSigs = new HashSet<MyNodeSig>(gen.allowedSigs);
		this.eleBound = gen.eleBound;
		this.varBound = new RefBound(gen.varBound);
		this.execBound = new RefBound(gen.execBound);
		this.bindTargetBound = new RefBound(gen.bindTargetBound);
		this.allowedConst = new HashSet<Object>(gen.allowedConst);
		this.constBound = gen.constBound;
		this.allowCast = gen.allowCast;
		this.allowNull = gen.allowNull;
		//this.typeBound = new RefBound(gen.typeBound);
	}

	class EnumerateScanner extends CtScanner {

		StaticAnalyzer ana;
		MyCtNode tree;
		EnumerateGenerator gen;
		CtElement ignoreInside;
		boolean failed;


		EnumerateScanner(StaticAnalyzer ana, MyCtNode tree) {
			// Just don't want to type this long shit
			gen = EnumerateGenerator.this;
			this.ana = ana;
			this.tree = tree;
			this.failed = false;
			this.ignoreInside = null;
		}

		@Override
		protected void enter(CtElement n) {
			// We are going to skip this
			if (ignoreInside != null) return;
			
			// We are going to collapse text message here
			if (Config.collapseTextMsg)
				if (MyCtNode.isTextMessage(n)) {
					gen.eleBound ++;
					gen.allowedSigs.add(new MyNodeSig(CtLiteralImpl.class, false));
					gen.constBound ++;
					gen.allowedConst.add(Config.textMsgToken);
					ignoreInside = n;
					return;
				}
			
			gen.allowedSigs.add(new MyNodeSig(n, false));
			// We do not handle these complicated statements in enumerateGenerator now
			if (n instanceof CtIf || n instanceof CtSwitch || n instanceof CtLoop || 
				n instanceof CtBlock || n instanceof CtCase) {
				failed = true;
				return;
			}
			if (n instanceof CtThrow) {
				CtThrow throwEle = (CtThrow) n;
				// XXX: so far I only handle this kind of thrown generation that immediately new an exception
				if (!(throwEle.getThrownExpression() instanceof CtConstructorCall)) {
					failed = true;
					return;
				}
				else {
					CtConstructorCall<?> cCall = (CtConstructorCall<?>) throwEle.getThrownExpression();
					if (!ana.isExceptionType(cCall.getExecutable().getDeclaringType())) {
						failed = true;
						return;
					}
				}
			}
			if (n instanceof CtField || n instanceof CtNewClass || n instanceof CtLocalVariable || 
				n instanceof CtParameter || n instanceof CtNewArray || n instanceof CtMethod || n instanceof CtPackage) {
				failed = true;
				return;
			}
			
			// XXX: We cannot generate TypeAccess if it is root!
			// This avoids to split x.y to two generators for x and y saperately.
			if (n instanceof CtTypeAccess)
				if (tree.getRawObject() == n) {
					failed = true;
					return;
				}

			if (n instanceof CtBinaryOperatorImpl) {
				CtBinaryOperatorImpl<?> op = (CtBinaryOperatorImpl<?>) n;
				gen.allowedSigs.add(new MyNodeSig(op.getKind(), false));
			}
			if (n instanceof CtOperatorAssignmentImpl) {
				CtOperatorAssignmentImpl<?, ?> op = (CtOperatorAssignmentImpl<?, ?>) n;
				gen.allowedSigs.add(new MyNodeSig(op.getKind(), false));
			}
			if (n instanceof CtUnaryOperatorImpl) {
				CtUnaryOperatorImpl<?> op = (CtUnaryOperatorImpl<?>) n;
				gen.allowedSigs.add(new MyNodeSig(op.getKind(), false));
			}

			// XXX: CtThisAccess is free of eleCount 
			if (!(n instanceof CtThisAccessImpl))
				gen.eleBound ++;
			
			if (n instanceof CtLiteral) {
				CtLiteral<?> l = (CtLiteral<?>) n;
				Object v = l.getValue();
				// For InstanceOf, we will just handle it specially
				if (v instanceof CtTypeReference) {
					// This is RHS of instanceof, we need to handle it together with 
					// InstanceOf BinaryOperator and we only try types inside the function
					if (n == tree.getRawObject() || !ana.inFuncType((CtTypeReference<?>) v)) {
						failed = true;
						return;
					}
				}
				else {
					gen.constBound ++;
					gen.allowedConst.add(v);
				}
			}

			if (n instanceof CtExpression) {
				CtExpression<?> exp = (CtExpression<?>) n;
				// We do not handle the typecast without context at root
				// and we do not handle the typecast for primitive-widening
				if (exp.getTypeCasts().size() != 0) {
					boolean hasPrimitive = false;
					for (CtTypeReference<?> t : exp.getTypeCasts())
						if (t.isPrimitive()) {
							hasPrimitive = true;
							break;
						}
					if (n == tree.getRawObject() || hasPrimitive) {
						failed = true;
						return;
					}
					gen.allowCast = true;
				}

				// XXX: right now we do not handle super access
				if (n instanceof CtSuperAccessImpl) {
					failed = true;
					return;
				}

				boolean isBinding = false;
				if (n instanceof CtVariableAccess) {
					CtVariableAccess<?> va = (CtVariableAccess<?>) n;
					isBinding = checkVarReference(va.getVariable());
				}

				if (n instanceof CtInvocation) {
					CtInvocation<?> inv = (CtInvocation<?>) n;
					CtExecutableReference<?> exec = inv.getExecutable();
					// XXX: Invoking a constructor with invocation is only for super()
					// I am not going to handle it.
					if (exec.isConstructor()) {
						failed = true;
						return;
					}
					isBinding = checkExecReference(exec);
				}

				if (n instanceof CtConstructorCall) {
					CtConstructorCall<?> newc = (CtConstructorCall<?>) n;
					isBinding = checkExecReference(newc.getExecutable());
					// For new class constructor, we are only going to do in package calls
					if (isBinding && (!(n.getParent() instanceof CtThrow) || (n == tree.getRawObject()))) {
						failed = true;
						return;
					}
				}

				if (isBinding && !(n instanceof CtConstructorCall)) {
					// So we need to count the targeted reference
					CtTargetedExpression<?, ?> taracc = (CtTargetedExpression<?, ?>) n;
					CtExpression<?> target = taracc.getTarget();
					// Target might be null if it is an this access, or type access
					if (target != null) {
						CtTypeReference<?> tref = target.getType();
						// XXX: Not sure why this happens, probably a bug in spoon
						if (tref == null) {
							if (target instanceof CtVariableAccess)
								tref = ((CtVariableAccess<?>) target).getVariable().getType();
							else if (target instanceof CtCodeSnippetExpressionImpl) {
								// OK, I really do not know why Spoon will generate this kind of shit in rare cases
								// Just f*** it
								failed = true;
								return;
							}
						}
						// XXX: Let it go for now and we need to investigate the log to see why
						if (tref == null) {
							System.out.println("[WARN] Bind tref is null???");
							System.out.println(n.toString());
							// Just f*** it
							failed = true;
							return;
						}
						checkBindTypeReference(tref);
					}
					else if (n instanceof CtFieldAccess){
						// Assume this is a static field access 
						CtFieldAccess<?> facc = (CtFieldAccess<?>)n;
						CtTypeReference<?> tref = facc.getVariable().getDeclaringType();
						checkBindTypeReference(tref);
						// Additional sanity check 
						// Make sure it is in our binding
						// Although I really do not know why it may not be in
						ArrayList<CtTypeReference<?>> tlist = new ArrayList<CtTypeReference<?>>();
						tlist.add(tref);
						ArrayList<CtFieldReference<Object>> frefs = ana.getFieldInBinding(tlist);
						boolean found = false;
						for (CtFieldReference<Object> fref : frefs) {
							if (fref.toString().equals(facc.getVariable().toString())) {
								found = true;
								break;
							}
						}
						if (!found) {
							failed = true;
							return;
						}
					}
					else if (n instanceof CtInvocation) {
						CtInvocation<?> invo = (CtInvocation<?>)n;
						CtTypeReference<?> tref = invo.getExecutable().getDeclaringType();
						checkBindTypeReference(tref);
						// Additional sanity check 
						// Make sure it is in our binding
						// Although I really do not know why it may not be in
						ArrayList<CtTypeReference<?>> tlist = new ArrayList<CtTypeReference<?>>();
						tlist.add(tref);
						ArrayList<CtExecutableReference<Object>> frefs = ana.getMethodInBinding(tlist);
						boolean found = false;
						for (CtExecutableReference<Object> eref : frefs) {
							if (eref.toString().equals(invo.getExecutable().toString())) {
								found = true;
								break;
							}
						}
						if (!found) {
							failed = true;
							return;
						}
					}
					else {
						// XXX: probably we should declare failure? I am not sure
						// I need to check here later
						//assert(false);
					}
				}
			}
		}
		
		@Override
		protected void exit(CtElement n) {
			// clear this flag
			if (n == ignoreInside)
				ignoreInside = null;
		}

		private boolean checkVarReference(CtVariableReference<?> n) {
			if (ana.inBefore(n))
				gen.varBound.incInBefore();
			else if (ana.inFunc(n))
				gen.varBound.incInFunc();
			else if (ana.inFile(n))
				gen.varBound.incInFile();
			else if (ana.inBinding(n)) {
				gen.varBound.incInBinding();
				return true;
			}
			else {
				failed = true;
			}
			return false;
		}

		private boolean checkExecReference(CtExecutableReference<?> n) {
			if (ana.inBefore(n))
				gen.execBound.incInBefore();
			else if (ana.inFunc(n))
				gen.execBound.incInFunc();
			else if (ana.inFile(n))
				gen.execBound.incInFile();
			else if (ana.inBinding(n)) {
				gen.execBound.incInBinding();
				return true;
			}
			else
				failed = true;
			return false;
		}

		private void checkBindTypeReference(CtTypeReference<?> n) {
			if (ana.inBeforeBindType(n))
				gen.bindTargetBound.incInBefore();
			else if (ana.inFuncBindType(n))
				gen.bindTargetBound.incInFunc();
			else if (ana.inFileBindType(n))
				gen.bindTargetBound.incInFile();
			else if (ana.inBindingBindType(n))
				gen.bindTargetBound.incInBinding();
			else
				failed = true;
		}

		/*
		private void checkTypeReference(CtTypeReference<?> n) {
			if (ana.inTree(n))
				gen.typeBound.inBefore ++;
			else if (ana.inFunc(n))
				gen.typeBound.inFunc ++;
			else
				gen.typeBound.all ++;
		}*/
	}

	protected static EnumerateGenerator createGenerator(MyCtNode tree, MyCtNode before, VarTypeContext typeContext) {
		if (tree.isTrait() && !tree.isNull())
			return null;
		if (tree.isReference() || tree.isColClass(CtReference.class))
			return null;
		EnumerateGenerator ret = new EnumerateGenerator();
		ret.rootSig = tree.nodeSig();
		// XXX: It might be the case that the whole shit is a collapsed textmsg
		if (Config.collapseTextMsg) {
			if (tree.isTextMessageNode())
				if (!ret.rootSig.isSuperOrEqual(new MyNodeSig(CtExpressionImpl.class, false)))
					ret.rootSig = ret.rootSig.getCommonSuperSig(new MyNodeSig(CtExpressionImpl.class, false));
			if (tree.isCollection()) {
				int n = tree.getNumChildren();
				for (int i = 0; i < n; i++)
					if (tree.getChild(i).isTextMessageNode()) 
						if (!ret.rootSig.isSuperOrEqual(new MyNodeSig(CtExpressionImpl.class, true))) {
							ret.rootSig = ret.rootSig.getCommonSuperSig(new MyNodeSig(CtExpressionImpl.class, true));
							break;
						}
			}
		}
		ret.allowedSigs = new HashSet<MyNodeSig>();
		ret.eleBound = 0;
		//ret.typeBound = new RefBound(0, 0, 0);
		ret.varBound = new RefBound(0, 0, 0, 0);
		ret.execBound = new RefBound(0, 0, 0, 0);
		ret.bindTargetBound = new RefBound(0, 0, 0, 0);
		ret.allowedConst = new HashSet<Object>();
		if (Config.presetConstants) {
			ret.allowedConst.add(0);
			ret.allowedConst.add(1);
			ret.allowedConst.add(null);
			ret.allowedConst.add(false);
		}
		ret.constBound = 0;
		ret.allowCast = false;
		ret.allowNull = tree.isNull();
		// Simply a null tree, we are going to just return
		if (ret.allowNull) return ret;
		
		StaticAnalyzer ana = new StaticAnalyzer(before);
		EnumerateScanner s = ret.new EnumerateScanner(ana, tree);
		tree.acceptScanner(s);
		// If the eleBound is more than 10, it does not make sense
		// given the current computation resources.
		int bound = ret.eleBound;
		//bound += ret.varBound.inBinding() + ret.execBound.inBinding();
		//if (ret.allowCast) bound ++;
		// XXX: Not allow random strings
		/*for (Object co : ret.allowedConst) {
			if (co instanceof String)
				if (((String) co).length() > 4)
					if (((String) co).contains("%"))
						return null;
		}*/
		if (s.failed || bound > Config.enumGenEleBound)
			return null;
		else
			return ret;
	}

	EnumerateGenerator mergeWith(EnumerateGenerator a) {
		EnumerateGenerator ret = new EnumerateGenerator();
		ret.rootSig = rootSig.getCommonSuperSig(a.rootSig);
		ret.allowedSigs = new HashSet<MyNodeSig>(allowedSigs);
		ret.allowedSigs.addAll(a.allowedSigs);
		ret.eleBound = eleBound > a.eleBound ? eleBound : a.eleBound;
		//ret.typeBound = typeBound.mergeWith(a.typeBound);
		ret.varBound = varBound.mergeWith(a.varBound);
		ret.execBound = execBound.mergeWith(a.execBound);
		ret.bindTargetBound = bindTargetBound.mergeWith(a.bindTargetBound);
		ret.allowedConst = new HashSet<Object>(allowedConst);
		ret.allowedConst.addAll(a.allowedConst);
		ret.constBound = constBound > a.constBound ? constBound : a.constBound;
		ret.allowCast = allowCast || a.allowCast;
		ret.allowNull = allowNull || a.allowNull;
		return ret;
	}

	public static EnumerateGenerator createGenerator(ArrayList<MyCtNode> trees, ArrayList<MyCtNode> befores, ArrayList<VarTypeContext> contexts) {
		assert(trees.size() > 0);
		EnumerateGenerator ret = createGenerator(trees.get(0), befores.get(0), contexts.get(0));
		if (ret == null)
			return null;
		for (int i = 1; i < trees.size(); i++) {
			EnumerateGenerator tmp = createGenerator(trees.get(i), befores.get(i), contexts.get(1));
			if (tmp == null)
				return null;
			ret = ret.mergeWith(tmp);
		}
		// If the root turns to Object, we are going to give up
		if (ret.rootSig.equals(new MyNodeSig(Object.class, false)) ||
			ret.rootSig.equals(new MyNodeSig(Object.class, true)))
			return null;
		
		int bound = ret.eleBound;
		bound += ret.varBound.inBinding() + ret.execBound.inBinding();
		if (ret.allowCast) bound ++;
		if (bound > Config.enumGenEleBound)
			return null;
		
		return ret;
	}

	@Override
	public String toString() {
		String ret = "rootSig: " + rootSig.toString() + "\n";
		ret += "allowedSigs: " + allowedSigs.toString() + "\n";
		ret += "eleBound: " + Integer.toString(eleBound) + "\n";
		//ret += "typeBound: " + typeBound.toString() + "\n";
		ret += "execBound: " + execBound.toString() + "\n";
		ret += "varBound: " + varBound.toString() + "\n";
		if (bindTargetBound != null)
			ret += "bindTargetBound: " +  bindTargetBound.toString() + "\n";
		ret += "constBound: " + Integer.toString(constBound) + "\n";
		ret += "allowedConst: " + allowedConst.toString() + "\n";
		ret += "allowCast: " + Boolean.toString(allowCast) + "\n";
		ret += "allowNull: " + Boolean.toString(allowNull) + "\n";
		return ret;
	}

	@Override
	public boolean generatorEquals(VarGenerator g) {
		if (!(g instanceof EnumerateGenerator)) return false;
		EnumerateGenerator a = (EnumerateGenerator) g;
		if (!rootSig.equals(a.rootSig))
			return false;
		if (!allowedSigs.equals(a.allowedSigs))
			return false;
		if (eleBound != a.eleBound)
			return false;
		if (!execBound.equals(a.execBound))
			return false;
		//if (!typeBound.equals(a.typeBound)) return false;
		if (!varBound.equals(a.varBound))
			return false;
		if (!bindTargetBound.equals(a.bindTargetBound))
			return false;
		if (!allowedConst.equals(a.allowedConst))
			return false;
		if (constBound != a.constBound)
			return false;
		if (allowCast != a.allowCast)
			return false;
		if (allowNull != a.allowNull)
			return false;
		return true;
	}

	static class GenRecord {

		MyCtNode n;
		int eleUsed, constBUsed;
		RefBound varUsed, execUsed, bindTargetUsed;/*, typeUsed;*/
		HashSet<Object> constUsed;

		public GenRecord() {
			this.n = null;
			this.eleUsed = 0;
			this.constBUsed = 0;
			this.varUsed = new RefBound();
			this.execUsed = new RefBound();
			this.bindTargetUsed = new RefBound();
			//this.typeUsed = new RefBound();
			this.constUsed = new HashSet<Object>();
		}

		public GenRecord(MyCtNode n) {
			this();
			this.n = n;
		}

		public void add(GenRecord r) {
			eleUsed += r.eleUsed;
			constBUsed += r.constBUsed;
			varUsed.add(r.varUsed);
			execUsed.add(r.execUsed);
			//typeUsed.add(r.typeUsed);
			bindTargetUsed.add(r.bindTargetUsed);
			constUsed.addAll(r.constUsed);
		}

		@Override
		public String toString() {
			return super.toString() + ":" + n.toString();
		}
	}

	private boolean updateWithRecord(GenRecord rec) {
		assert( eleBound >= rec.eleUsed);
		eleBound -= rec.eleUsed;
		assert( constBound >= rec.constBUsed);
		constBound -= rec.constBUsed;
		boolean ret = varBound.subs(rec.varUsed);
		//typeBound.subs(rec.typeUsed);
		ret = execBound.subs(rec.execUsed) && ret;
		ret = bindTargetBound.subs(rec.bindTargetUsed) && ret;
		return ret;
		// XXX: We allow multiple uses of each-const
		//allowedConst.removeAll(rec.constUsed);
	}

	private static ArrayList<GenRecord> generateImpl(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();

		// Empty list always works
		if (gen.rootSig.isCollection()) {
			GenRecord r2 = new GenRecord(new MyCtNode(new ArrayList<CtElement>(), false));
			ret.add(r2);
		}
		else if (gen.rootSig.isAssignableFrom(CtThisAccessImpl.class))
			ret.addAll(genThisAccess(before, f, gen, ana));

		if ((gen.eleBound == 0) || (gen.varBound.isZero() && gen.allowedConst.size() == 0 &&
				gen.execBound.isZero() && !gen.allowedSigs.contains(new MyNodeSig(CtReturnImpl.class, false))))
			return ret;
		if (gen.rootSig.isCollection()) {
			EnumerateGenerator tmp = new EnumerateGenerator(gen);
			tmp.rootSig = tmp.rootSig.elementSig();
			ArrayList<GenRecord> a = generateImpl(before, f, tmp, ana);
			if (a.size() == 0) return ret;
			for (GenRecord r : a) {
				tmp = new EnumerateGenerator(gen);
				boolean ret1 = tmp.updateWithRecord(r);
				if (!ret1) continue;
				// XXX: We require this to avoid recursion
				// This effectively turn off single thisaccess 
				if (r.eleUsed == 0)
					tmp.allowedSigs.remove(new MyNodeSig(CtThisAccessImpl.class, false));
				ArrayList<GenRecord> b = generateImpl(before, f, tmp, ana);
				for (GenRecord r2 : b) {
					r2.n.collectionAdd(r.n);
					r2.add(r);
					ret.add(r2);
				}
			}
			return ret;
		}
		else {
			// XXX: skipped CtAnnotationImpl
			if (gen.rootSig.isAssignableFrom(CtBinaryOperatorImpl.class))
				ret.addAll(genBinaryOperator(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtConditionalImpl.class))
				ret.addAll(genConditional(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtLiteralImpl.class))
				ret.addAll(genLiteral(before, f, gen, ana));
			// XXX: skipped CtNewArrayImpl
			if (gen.rootSig.isAssignableFrom(CtArrayReadImpl.class))
				ret.addAll(genArrayRead(before, f, gen, ana));
			// XXX: skipped CtNewClassImpl
			if (gen.rootSig.isAssignableFrom(CtConstructorCallImpl.class))
				ret.addAll(genConstructorCall(before, f, gen, false, ana));
			// XXX: skipped CtExecutableReferenceImpl
			if (gen.rootSig.isAssignableFrom(CtInvocationImpl.class))
				ret.addAll(genInvocation(before, f, gen, ana));
			// XXX: skipped CtThisAccess
			// XXX: skipped CtTyepAccess, because we are only going to see it with static access
			if (gen.rootSig.isAssignableFrom(CtUnaryOperatorImpl.class))
				ret.addAll(genUnaryOperator(before, f, gen, ana));
			// XXX: skipped CtAnnotationFieldAccessImpl
			if (gen.rootSig.isAssignableFrom(CtFieldReadImpl.class))
				ret.addAll(genFieldRead(before, f, gen, ana));
			// XXX: skipped CtSuperAccessImpl
			if (gen.rootSig.isAssignableFrom(CtVariableReadImpl.class))
				ret.addAll(genVariableRead(before, f, gen, ana));

			// We are going to deal with assignment as well
			if (gen.rootSig.isAssignableFrom(CtAssignmentImpl.class))
				ret.addAll(genAssignment(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtOperatorAssignmentImpl.class))
				ret.addAll(genOperatorAssignment(before, f, gen, ana));

			// We are only going to do this if we cannot use read, this is to avoid redundancy 
			if (gen.rootSig.isAssignableFrom(CtArrayWriteImpl.class))
				if (!gen.rootSig.isAssignableFrom(CtArrayReadImpl.class) || 
					!gen.allowedSigs.contains(new MyNodeSig(CtArrayReadImpl.class, false)))
					ret.addAll(genArrayWrite(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtFieldWriteImpl.class))
				if (!gen.rootSig.isAssignableFrom(CtFieldReadImpl.class) ||
					!gen.allowedSigs.contains(new MyNodeSig(CtFieldReadImpl.class, false)))
					ret.addAll(genFieldWrite(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtVariableWriteImpl.class))
				if (!gen.rootSig.isAssignableFrom(CtVariableReadImpl.class) ||
					!gen.allowedSigs.contains(new MyNodeSig(CtVariableReadImpl.class, false)))
					ret.addAll(genVariableWrite(before, f, gen, ana));
			
			if (gen.rootSig.isAssignableFrom(CtReturnImpl.class))
				ret.addAll(genReturn(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtBreakImpl.class))
				ret.addAll(genBreak(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtContinueImpl.class))
				ret.addAll(genContinue(before, f, gen, ana));
			if (gen.rootSig.isAssignableFrom(CtThrowImpl.class))
				ret.addAll(genThrow(before, f, gen, ana));
			
			// XXX: We are just going to fill whatever possible to those holes,
			// We will use TypeHelper to check it later
			return ret;
		}
	}

	/*private static CtTypeReference<?> getDeclaringTypeRef(MyCtNode tree) {
		CtReference ref = (CtReference)tree.getRawObject();
		CtTypeReference<?> tref = null;
		if (ref instanceof CtFieldReference)
			tref = ((CtFieldReference<?>) ref).getDeclaringType();
		else if (ref instanceof CtExecutableReference)
			tref = ((CtExecutableReference<?>) ref).getDeclaringType();
		return tref;
	}*/

	private static ArrayList<GenRecord> genOperatorAssignment(MyCtNode before, Factory f,
			EnumerateGenerator gen, StaticAnalyzer ana) {
		return genAssignmentImpl(before, f, gen, true, ana);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genAssignmentImpl(MyCtNode before, Factory f, EnumerateGenerator gen,
			boolean opass, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (opass) {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtOperatorAssignmentImpl.class, false)))
				return ret;
		}
		else {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtAssignmentImpl.class, false)))
				return ret;
		}
		if (gen.eleBound <= 0) return ret;
		if (gen.varBound.isZero()) return ret;
		ArrayList<GenRecord> lefts = new ArrayList<GenRecord>();
		if (gen.allowedSigs.contains(new MyNodeSig(CtVariableWriteImpl.class, false)))
			lefts.addAll(genVariableWrite(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtVariableWriteImpl.class), ana));
		if (gen.allowedSigs.contains(new MyNodeSig(CtFieldWriteImpl.class, false)))
			lefts.addAll(genFieldWrite(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtFieldWriteImpl.class), ana));
		if (gen.allowedSigs.contains(new MyNodeSig(CtArrayWriteImpl.class, false)))
			lefts.addAll(genArrayWrite(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtArrayWriteImpl.class), ana));
		for (GenRecord rl : lefts) {
			EnumerateGenerator tmp = gen.cloneWithOneLessEle();
			boolean ret1 = tmp.updateWithRecord(rl);
			if (!ret1) continue;
			ArrayList<GenRecord> rights = generateImpl(before, f, tmp.cloneWithRootSig(CtExpressionImpl.class), ana);
			for (GenRecord rr : rights) {
				boolean needcast = false;
				if (!TypeHelper.isCompatibleType(rl.n, rr.n)) {
					if (gen.allowCast)
						needcast = true;
					else
						continue;
				}
				CtExpression<Object> lexp = (CtExpression<Object>) rl.n.getRawObject();
				CtExpression<Object> rexp = (CtExpression<Object>) rr.n.getRawObject();
				if (needcast) {
					ArrayList<CtTypeReference<?>> refs = new ArrayList<CtTypeReference<?>>();
					refs.add(lexp.getType());
					rexp.setTypeCasts(refs);
				}
				if (opass) {
					for (MyNodeSig sig : gen.allowedSigs) {
						if (!sig.isBinop()) continue;
						CtOperatorAssignment<Object, Object> oass = f.Core().createOperatorAssignment();
						oass.setAssignment(rexp);
						oass.setAssigned(lexp);
						oass.setKind(sig.getBinop());
						if (!TypeHelper.inferType(oass)) continue;
						GenRecord rec = new GenRecord(new MyCtNode(oass, false));
						rec.add(rl);
						rec.add(rr);
						rec.eleUsed ++;
						ret.add(rec);
					}
				}
				else {
					CtAssignment<Object, Object> ass = f.Core().createAssignment();
					ass.setAssignment(rexp);
					ass.setAssigned(lexp);
					if (!TypeHelper.inferType(ass)) continue;
					GenRecord rec = new GenRecord(new MyCtNode(ass, false));
					rec.add(rl);
					rec.add(rr);
					rec.eleUsed ++;
					ret.add(rec);
				}
			}
		}
		return ret;
	}

	private static ArrayList<GenRecord> genAssignment(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		return genAssignmentImpl(before, f, gen, false, ana);
	}

	private static ArrayList<GenRecord> genVariableAccess(MyCtNode before, Factory f,EnumerateGenerator gen,
			boolean iswrite, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtVariableAccessImpl.class, false)) &&
			!gen.allowedSigs.contains(new MyNodeSig(CtVariableReadImpl.class, false)) &&
			!gen.allowedSigs.contains(new MyNodeSig(CtVariableWriteImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		if (gen.varBound.isZero()) return ret;
		ArrayList<CtVariableReference<Object>> candidates = null;
		if (gen.varBound.inFile() > 0 || gen.varBound.inBinding() > 0)
			candidates = ana.getVarInFile(f);
		else if (gen.varBound.inFunc() > 0)
			candidates = ana.getVarInFunc(f);
		else
			candidates = ana.getVarInBefore(f);
		for (CtVariableReference<Object> vref : candidates) {
			CtVariableAccess<Object> acc = null;
			if (iswrite)
				acc = f.Core().createVariableWrite();
			else
				acc = f.Core().createVariableRead();
			acc.setVariable(vref);
			if (!TypeHelper.inferType(acc, gen.allowCast)) continue;
			GenRecord rec = new GenRecord(new MyCtNode(acc, false));
			rec.eleUsed ++;
			if (ana.inBefore(vref))
				rec.varUsed.incInBefore();
			else if (ana.inFunc(vref))
				rec.varUsed.incInFunc();
			else
				rec.varUsed.incInFile();
			ret.add(rec);
		}
		return ret;
	}

	private static ArrayList<GenRecord> genVariableRead(MyCtNode before, Factory f,EnumerateGenerator gen, StaticAnalyzer ana) {
		return genVariableAccess(before, f, gen, false, ana);
	}

	private static ArrayList<GenRecord> genVariableWrite(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		return genVariableAccess(before, f, gen, true, ana);
	}

	@SuppressWarnings({"unchecked" })
	private static ArrayList<GenRecord> genFieldAccess(MyCtNode before, Factory f,EnumerateGenerator gen,
			boolean iswrite, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (iswrite) {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtFieldWriteImpl.class, false)))
				return ret;
		}
		else {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtFieldReadImpl.class, false)))
				return ret;
		}
		if (gen.eleBound <= 0) return ret;
		if (gen.varBound.isZero()) return ret;
		ArrayList<CtFieldReference<Object>> candidates = null;
		if (gen.varBound.inBinding() > 0) {
			candidates = ana.getFieldInFile(f);
			ArrayList<CtTypeReference<?>> trefs = null;
			if (gen.bindTargetBound.inBinding() > 0)
				trefs = ana.getBindTypeInBinding();
			else if (gen.bindTargetBound.inFile() > 0)
				trefs = ana.getBindTypeInFile();
			else if (gen.bindTargetBound.inFunc() > 0)
				trefs = ana.getBindTypeInFunc();
			else if (gen.bindTargetBound.inBefore() > 0)
				trefs = ana.getBindTypeInBefore();
			else {
				if (ana.getThisTypeReference() != null)
					trefs = new ArrayList<CtTypeReference<?>>(Arrays.asList(ana.getThisTypeReference()));
				else
					trefs = new ArrayList<CtTypeReference<?>>();
			}
			candidates.addAll(ana.getFieldInBinding(trefs));
		}
		else if (gen.varBound.inFile() > 0)
			candidates = ana.getFieldInFile(f);
		else if (gen.varBound.inFunc() > 0)
			candidates = ana.getFieldInFunc(f);
		else
			candidates = ana.getFieldInBefore(f);
		for (CtFieldReference<Object> fref : candidates) {
			boolean isLength = fref.getSimpleName().equals("length");
			// If it is enum, then we also need typeaccess
			boolean nonStaticAcc = (!fref.isStatic()) && !(StaticAnalyzer.getDeclaration(fref.getDeclaringType()) instanceof CtEnum);
			if (nonStaticAcc) {
				boolean doTarget = true;
				EnumerateGenerator tmp = new EnumerateGenerator(gen);
				tmp.eleBound --;
				if (ana.inBefore(fref))
					tmp.varBound.decInBefore();
				else if (ana.inFunc(fref))
					tmp.varBound.decInFunc();
				else if (ana.inFile(fref))
					tmp.varBound.decInFile();
				else {
					tmp.varBound.decInBinding();
					if (tmp.bindTargetBound.isZero())
						doTarget = false;
					else
						tmp.bindTargetBound.decInBefore();
				}

				if (doTarget) {
					// We force to allow ThisAccess
					EnumerateGenerator tGen = tmp.cloneWithRootSig(CtExpression.class);
					tGen.allowedSigs.add(new MyNodeSig(CtThisAccessImpl.class, false));
					ArrayList<GenRecord> tmprecs = generateImpl(before, f, tGen, ana);
					for (GenRecord r : tmprecs) {
						CtFieldAccess<Object> acc = null;
						if (iswrite)
							acc = f.Core().createFieldWrite();
						else
							acc = f.Core().createFieldRead();
						boolean needCast = false;
						if (isLength) {
							if (!TypeHelper.isArrayType(r.n)) continue;
						}
						else {
							if (!TypeHelper.isCompatibleType(fref.getDeclaringType(), r.n)) {
								if (gen.allowCast)
									needCast = true;
								else
									continue;
							}
						}
						CtExpression<?> t = (CtExpression<?>) r.n.getRawObject();
						if (needCast)
							t.addTypeCast(fref.getDeclaringType());
						acc.setTarget(t);
						acc.setVariable(fref);
						if (!TypeHelper.inferType(acc, gen.allowCast)) continue;
						GenRecord rec = new GenRecord(new MyCtNode(acc, false));
						rec.add(r);
						rec.eleUsed ++;
						if (ana.inBefore(fref))
							rec.varUsed.incInBefore();
						else if (ana.inFunc(fref))
							rec.varUsed.incInFunc();
						else if (ana.inFile(fref))
							rec.varUsed.incInFile();
						else {
							CtTypeReference<?> targetType = ((CtExpression<?>) r.n.getRawObject()).getType();

							if (ana.inBeforeBindType(targetType))
								rec.bindTargetUsed.incInBefore();
							else if (ana.inFuncBindType(targetType))
								rec.bindTargetUsed.incInFunc();
							else if (ana.inFileBindType(targetType))
								rec.bindTargetUsed.incInFile();
							else
								rec.bindTargetUsed.incInBinding();
							rec.varUsed.incInBinding();
						}
						ret.add(rec);
					}
				}

				/*CtTypeReference<?> freft = fref.getDeclaringType();
				if (freft.getDeclaration() instanceof CtEnum)
					freft = freft.getDeclaringType();*/
				/*if (ana.getThisTypeReference() != null)
					if (TypeHelper.typeCompatible(fref.getDeclaringType(), ana.getThisTypeReference())) {
						CtThisAccess<?> thisacc = f.Code().createThisAccess(fref.getDeclaringType());
						CtFieldAccess<Object> acc = null;
						if (iswrite)
							acc = f.Core().createFieldWrite();
						else
							acc = f.Core().createFieldRead();
						acc.setTarget(thisacc);
						acc.setVariable(fref);
						if (!TypeHelper.inferType(acc)) continue;
						GenRecord rec = new GenRecord(new MyCtNode(acc, false));
						rec.eleUsed ++;
						if (ana.inBefore(fref))
							rec.varUsed.incInBefore();
						else if (ana.inFunc(fref))
							rec.varUsed.incInFunc();
						else if (ana.inFile(fref))
							rec.varUsed.incInFile();
						else
							rec.varUsed.incInBinding();
						ret.add(rec);
					}*/
			}
			// .class field is very special. It is a language feature actually
			if (!nonStaticAcc || fref.getSimpleName().equals("class")) {
				CtTypeAccess<Object> taccess = f.Core().createTypeAccess();
				taccess.setType((CtTypeReference<Object>)fref.getDeclaringType());
				CtFieldAccess<Object> acc = null;
				if (iswrite)
					acc = f.Core().createFieldWrite();
				else
					acc = f.Core().createFieldRead();
				acc.setTarget(taccess);
				acc.setVariable(fref);
				if (!TypeHelper.inferType(acc, gen.allowCast)) continue;
				GenRecord rec = new GenRecord(new MyCtNode(acc, false));
				rec.eleUsed ++;
				if (ana.inBefore(fref))
					rec.varUsed.incInBefore();
				else if (ana.inFunc(fref))
					rec.varUsed.incInFunc();
				else if (ana.inFile(fref))
					rec.varUsed.incInFile();
				else {
					CtTypeReference<?> targetType = fref.getDeclaringType();
					if (ana.inBeforeBindType(targetType))
						rec.bindTargetUsed.incInBefore();
					else if (ana.inFuncBindType(targetType))
						rec.bindTargetUsed.incInFunc();
					else if (ana.inFileBindType(targetType))
						rec.bindTargetUsed.incInFile();
					else
						rec.bindTargetUsed.incInBinding();
					rec.varUsed.incInBinding();
				}
				ret.add(rec);
			}
		}
		return ret;
	}

	private static ArrayList<GenRecord> genFieldRead(MyCtNode before, Factory f,EnumerateGenerator gen, StaticAnalyzer ana) {
		return genFieldAccess(before, f, gen, false, ana);
	}

	private static ArrayList<GenRecord> genFieldWrite(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		return genFieldAccess(before, f, gen, true, ana);
	}

	private static boolean checkInSig(MyCtNode before, MyNodeSig sig) {
		MyCtNode parent = before.parentEleNode();
		MyNodeSig parentSig = parent.nodeSig();
		while (!parentSig.isAssignableFrom(CtPackageImpl.class)){
				if (sig.isAssignableFrom(parentSig)) { return true; }
				parent = parent.parentEleNode();
				parentSig = parent.nodeSig();
		}
		return false;
	}

	private static ArrayList<GenRecord> genContinue(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtContinueImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		if (!checkInSig(before, new MyNodeSig(CtLoop.class, false)))
			return ret;
		CtContinue contEle = f.Core().createContinue();
		contEle.setTargetLabel(null);
		GenRecord rec = new GenRecord(new MyCtNode(contEle, false));
		rec.eleUsed ++;
		ret.add(rec);
		
		Set<String> labels = ana.getEnclosingLabels();
		for (String label: labels) {
			contEle = f.Core().createContinue();
			contEle.setTargetLabel(label);
			rec = new GenRecord(new MyCtNode(contEle, false));
			rec.eleUsed ++;
			ret.add(rec);
		}
		
		return ret;
	}

	private static ArrayList<GenRecord> genBreak(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtBreakImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		if (!checkInSig(before, new MyNodeSig(CtLoop.class, false)))
			return ret;
		CtBreak breakEle = f.Core().createBreak();
		breakEle.setTargetLabel(null);
		GenRecord rec = new GenRecord(new MyCtNode(breakEle, false));
		rec.eleUsed ++;
		ret.add(rec);
		
		Set<String> labels = ana.getEnclosingLabels();
		for (String label: labels) {
			breakEle = f.Core().createBreak();
			breakEle.setTargetLabel(label);
			rec = new GenRecord(new MyCtNode(breakEle, false));
			rec.eleUsed ++;
			ret.add(rec);
		}
		
		return ret;
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genThrow(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtThrowImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		ArrayList<GenRecord> tmprecs = genConstructorCall(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtExpression.class), true, ana);
		for (GenRecord r : tmprecs) {
			CtThrow throwEle = f.Core().createThrow();
			throwEle.setThrownExpression((CtExpression<? extends Throwable>) r.n.getRawObject());
			GenRecord rec0 = new GenRecord(new MyCtNode(throwEle, false));
			rec0.add(r);
			rec0.eleUsed ++;
			ret.add(rec0);
		}
		
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genReturn(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtReturnImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		CtTypeReference<?> tRef = ana.getEnclosingFuncReturnType();
		
		CtReturn<Object> retEle;
		if (tRef == null || tRef.equals(f.Type().VOID_PRIMITIVE) || tRef.equals(f.Type().VOID)) {
			// No value return first
			retEle = f.Core().createReturn();
			
			retEle.setReturnedExpression(null);
			GenRecord rec0 = new GenRecord(new MyCtNode(retEle, false));
			rec0.eleUsed ++;
			ret.add(rec0);
		}
		else {
			ArrayList<GenRecord> tmprecs = generateImpl(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtExpression.class), ana);
			for (GenRecord r : tmprecs) {
				if (!TypeHelper.isCompatibleType(tRef, r.n)) continue;
				retEle = f.Core().createReturn();
				retEle.setReturnedExpression((CtExpression<Object>) r.n.getRawObject());
				GenRecord rec = new GenRecord(new MyCtNode(retEle, false));
				rec.add(r);
				rec.eleUsed ++;
				ret.add(rec);
			}
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genUnaryOperator(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtUnaryOperatorImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		ArrayList<GenRecord> tmprecs = generateImpl(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtExpression.class), ana);
		for (MyNodeSig sig : gen.allowedSigs) {
			if (!sig.isUop()) continue;
			for (GenRecord r : tmprecs) {
				CtUnaryOperator<Object> uop = f.Core().createUnaryOperator();
				CtExpression<Object> exp = (CtExpression<Object>) r.n.getRawObject();
				// We are not going to generate !(!X))
				if (sig.getUop() == UnaryOperatorKind.NOT)
					if (exp instanceof CtUnaryOperator)
						if (((CtUnaryOperator<?>) exp).getKind() == UnaryOperatorKind.NOT)
							continue;
				uop.setOperand(exp);
				uop.setKind(sig.getUop());
				if (!TypeHelper.inferType(uop)) continue;
				GenRecord rec = new GenRecord(new MyCtNode(uop, false));
				rec.add(r);
				rec.eleUsed ++;
				ret.add(rec);
			}
		}
		return ret;
	}
	
	private static ArrayList<GenRecord> genThisAccess(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtThisAccessImpl.class, false)))
			return ret;
		CtTypeReference<?> thistref = ana.getThisTypeReference();
		if (thistref == null)
			return ret;
		CtThisAccess<?> thisacc = f.Code().createThisAccess(thistref);
		GenRecord rec = new GenRecord(new MyCtNode(thisacc, false));
		ret.add(rec);
		return ret;
		/*
		if (thisTref != null)
			if (TypeHelper.typeCompatible(exec.getDeclaringType(), thisTref)) {
				CtThisAccess<?> thisacc = f.Code().createThisAccess(exec.getDeclaringType());
				ArrayList<GenRecord> argrecs = genArgumentList(before, f, exec, tmp, gen.allowCast);
				for (GenRecord r: argrecs) {
					List<CtExpression<?>> args = (List<CtExpression<?>>) r.n.getRawObject();
					CtInvocation<Object> inv = f.Code().createInvocation(thisacc, exec, args);
					if (!TypeHelper.inferType(inv)) continue;
					GenRecord rec = new GenRecord(new MyCtNode(inv, false));
					rec.add(r);
					rec.eleUsed ++;
					if (ana.inBefore(exec))
						rec.execUsed.incInBefore();
					else if (ana.inFunc(exec))
						rec.execUsed.incInFunc();
					else if (ana.inFile(exec))
						rec.execUsed.incInFile();
					else
						rec.execUsed.incInBinding();
					ret.add(rec);
				}
			}*/
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static ArrayList<GenRecord> genInvocation(MyCtNode before, Factory f,EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtInvocationImpl.class, false)))
			return ret;
		if (gen.execBound.isZero()) return ret;
		if (gen.eleBound <= 0) return ret;
		ArrayList<CtExecutableReference<Object>> candidates = null;
		if (gen.execBound.inBinding() > 0) {
			candidates = ana.getMethodInFile(f);
			ArrayList<CtTypeReference<?>> trefs = null;
			if (gen.bindTargetBound.inBinding() > 0)
				trefs = ana.getBindTypeInBinding();
			else if (gen.bindTargetBound.inFile() > 0)
				trefs = ana.getBindTypeInFile();
			else if (gen.bindTargetBound.inFunc() > 0)
				trefs = ana.getBindTypeInFunc();
			else if (gen.bindTargetBound.inBefore() > 0)
				trefs = ana.getBindTypeInBefore();
			else {
				if (ana.getThisTypeReference() != null)
					trefs = new ArrayList<CtTypeReference<?>>(Arrays.asList(ana.getThisTypeReference()));
				else
					trefs = new ArrayList<CtTypeReference<?>>();
			}
			candidates.addAll(ana.getMethodInBinding(trefs));
		}
		else if (gen.execBound.inFile() > 0)
			candidates = ana.getMethodInFile(f);
		else if (gen.execBound.inFunc() > 0)
			candidates = ana.getMethodInFunc(f);
		else
			candidates = ana.getMethodInBefore(f);
		for (CtExecutableReference<Object> exec : candidates) {
			EnumerateGenerator tmp = new EnumerateGenerator(gen);
			tmp.eleBound --;
			boolean doTarget = true;
			if (ana.inBefore(exec))
				tmp.execBound.decInBefore();
			else if (ana.inFunc(exec))
				tmp.execBound.decInFunc();
			else if (ana.inFile(exec))
				tmp.execBound.decInFile();
			else {
				tmp.execBound.decInBinding();
				if (tmp.bindTargetBound.isZero())
					doTarget = false;
				else
					tmp.bindTargetBound.decInBefore();
			}

			if (!exec.isStatic()) {
				if (doTarget) {
					// We force to allow this access 
					EnumerateGenerator tGen = tmp.cloneWithRootSig(CtExpression.class);
					tGen.allowedSigs.add(new MyNodeSig(CtThisAccessImpl.class, false));
					ArrayList<GenRecord> targetrecs = generateImpl(before, f, tGen, ana);
					for (GenRecord trec : targetrecs) {
						if (!TypeHelper.isCompatibleType(exec.getDeclaringType(), trec.n))
							continue;

						EnumerateGenerator tmp2 = new EnumerateGenerator(tmp);
						boolean ret1 = tmp2.updateWithRecord(trec);
						if (!ret1) continue;
						ArrayList<GenRecord> argrecs = genArgumentList(before, f, exec, tmp2, gen.allowCast, ana);
						for (GenRecord r: argrecs) {
							CtExpression<?> texp = (CtExpression<?>) trec.n.getRawObject();
							List<CtExpression<?>> args = (List<CtExpression<?>>) r.n.getRawObject();
							CtInvocation<Object> inv = f.Code().createInvocation(texp, exec, args);
							if (!TypeHelper.inferType(inv)) continue;
							GenRecord rec = new GenRecord(new MyCtNode(inv, false));
							rec.add(trec);
							rec.add(r);
							rec.eleUsed ++;
							if (ana.inBefore(exec))
								rec.execUsed.incInBefore();
							else if (ana.inFunc(exec))
								rec.execUsed.incInFunc();
							else if (ana.inFile(exec))
								rec.execUsed.incInFile();
							else {
								CtTypeReference targetType = exec.getDeclaringType();
								if (ana.inBeforeBindType(targetType))
									rec.bindTargetUsed.incInBefore();
								else if (ana.inFuncBindType(targetType))
									rec.bindTargetUsed.incInFunc();
								else if (ana.inFileBindType(targetType))
									rec.bindTargetUsed.incInFile();
								else
									rec.bindTargetUsed.incInBinding();
								rec.execUsed.incInBinding();
							}
							ret.add(rec);
						}
					}
				}
				/*
				CtTypeReference<?> thisTref = ana.getThisTypeReference();
				if (thisTref != null)
					if (TypeHelper.typeCompatible(exec.getDeclaringType(), thisTref)) {
						CtThisAccess<?> thisacc = f.Code().createThisAccess(exec.getDeclaringType());
						ArrayList<GenRecord> argrecs = genArgumentList(before, f, exec, tmp, gen.allowCast);
						for (GenRecord r: argrecs) {
							List<CtExpression<?>> args = (List<CtExpression<?>>) r.n.getRawObject();
							CtInvocation<Object> inv = f.Code().createInvocation(thisacc, exec, args);
							if (!TypeHelper.inferType(inv)) continue;
							GenRecord rec = new GenRecord(new MyCtNode(inv, false));
							rec.add(r);
							rec.eleUsed ++;
							if (ana.inBefore(exec))
								rec.execUsed.incInBefore();
							else if (ana.inFunc(exec))
								rec.execUsed.incInFunc();
							else if (ana.inFile(exec))
								rec.execUsed.incInFile();
							else
								rec.execUsed.incInBinding();
							ret.add(rec);
						}
					}*/
			}
			else {
				ArrayList<GenRecord> argrecs = genArgumentList(before, f, exec, tmp, gen.allowCast, ana);
				for (GenRecord r: argrecs) {
					CtTypeAccess<Object> taccess = f.Core().createTypeAccess();
					taccess.setType((CtTypeReference<Object>)exec.getDeclaringType());
					List<CtExpression<?>> args = (List<CtExpression<?>>) r.n.getRawObject();
					CtInvocation<Object> inv = f.Code().createInvocation(taccess, exec, args);
					if (!TypeHelper.inferType(inv)) continue;
					GenRecord rec = new GenRecord(new MyCtNode(inv, false));
					rec.add(r);
					rec.eleUsed ++;
					if (ana.inBefore(exec))
						rec.execUsed.incInBefore();
					else if (ana.inFunc(exec))
						rec.execUsed.incInFunc();
					else if (ana.inFile(exec))
						rec.execUsed.incInFile();
					else {
						CtTypeReference targetType = exec.getDeclaringType();
						if (ana.inBeforeBindType(targetType))
							rec.bindTargetUsed.incInBefore();
						else if (ana.inFuncBindType(targetType))
							rec.bindTargetUsed.incInFunc();
						else if (ana.inFileBindType(targetType))
							rec.bindTargetUsed.incInFile();
						else
							rec.bindTargetUsed.incInBinding();
						rec.execUsed.incInBinding();
					}
					ret.add(rec);
				}
			}
		}
		return ret;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ArrayList<GenRecord> genArgumentListImpl(MyCtNode before, Factory f, List<CtTypeReference<?>> paras, EnumerateGenerator gen, boolean allowVararg, boolean allowCast, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtThisAccessImpl.class, false)))
			if (gen.eleBound < paras.size()) return ret;
		CtTypeReference<?> lastArgTref = null;
		if (paras.size() > 0) {
			lastArgTref = paras.get(paras.size() - 1);
			if (allowVararg && (lastArgTref instanceof CtArrayTypeReference)) {
				ArrayList<CtTypeReference<?>> paras1 = new ArrayList<CtTypeReference<?>>(paras);
				paras1.remove(paras.size() - 1);
				ret.addAll(genArgumentListImpl(before, f, paras1, gen, false, allowCast, ana));
			}
		}
		if (paras.size() == 0) {
			GenRecord init = new GenRecord(new MyCtNode(new ArrayList<CtExpression<?>>(), false));
			ret.add(init);
			return ret;
		}
		
		EnumerateGenerator tmp = gen.cloneWithRootSig(CtExpressionImpl.class);
		tmp.eleBound -= paras.size() - 1;
		ArrayList<GenRecord> recs1 = generateImpl(before, f, tmp, ana);
		for (GenRecord r1 : recs1) {
			boolean compatible = false;
			boolean vCompatible = false;
			if (TypeHelper.isCompatibleType(lastArgTref, r1.n)) {
				compatible = true;
			}
			else if (allowCast && !allowVararg) {
				CtExpression exp = (CtExpression) r1.n.getRawObject();
				exp.addTypeCast(lastArgTref);
			}
			if (allowVararg) {
				// Thisaccess cannot be used solely for elements 
				if ((lastArgTref instanceof CtArrayTypeReference)) {
					CtTypeReference<?> lastCompTref = ((CtArrayTypeReference<?>) lastArgTref).getComponentType();
					if (TypeHelper.isCompatibleType(lastCompTref, r1.n))
						vCompatible = true;
				}
			}
			if (!compatible && !vCompatible) continue;
			EnumerateGenerator tmp2 = new EnumerateGenerator(gen);
			boolean ret1 = tmp2.updateWithRecord(r1);
			if (!ret1) continue;
			EnumerateGenerator tmp3 = new EnumerateGenerator(tmp2);
			ArrayList<CtTypeReference<?>> paras1 = new ArrayList<CtTypeReference<?>>(paras);
			ArrayList<GenRecord> recs2 = new ArrayList<GenRecord>();
			if (vCompatible) {
				// XXX: This is to avoid infinite recursion
				if (r1.n.getRawObject() instanceof CtThisAccessImpl)
					tmp2.allowedSigs.remove(new MyNodeSig(CtThisAccessImpl.class, false));
				recs2.addAll(genArgumentListImpl(before, f, paras1, tmp2, true, allowCast, ana));
			}
			if (compatible) {
				paras1.remove(paras1.size() - 1);
				recs2.addAll(genArgumentListImpl(before, f, paras1, tmp3, false, allowCast, ana));
			}
			for (GenRecord r2 : recs2) {
				r2.n.collectionAdd(r1.n);
				r2.add(r1);
				ret.add(r2);
			}
		}
		
		return ret;
	}

	private static ArrayList<GenRecord> genArgumentList(MyCtNode before, Factory f, CtExecutableReference<Object> exec,
			EnumerateGenerator gen, boolean allowCast, StaticAnalyzer ana) {
		List<CtTypeReference<?>> paras = exec.getParameters();
		return genArgumentListImpl(before, f, paras, gen, true, allowCast, ana);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genConstructorCall(MyCtNode before, Factory f, EnumerateGenerator gen, boolean exceptionOnly, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtConstructorCallImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		if (gen.execBound.isZero()) return ret;
		ArrayList<CtExecutableReference<Object>> candidates = null;
		// For now, we are only going to try constructors that is inside the package
		if (gen.execBound.inBinding() > 0 && exceptionOnly)
			candidates = ana.getExceptionConstructorInBinding();
		else if (gen.execBound.inFile() > 0)
			candidates = ana.getConstructorInFile(f);
		else if (gen.execBound.inFunc() > 0)
			candidates = ana.getConstructorInFunc(f);
		else
			candidates = ana.getConstructorInBefore(f);
		for (CtExecutableReference<Object> exec : candidates) {
			if (exceptionOnly) {
				CtTypeReference<?> texc = exec.getDeclaringType();
				if (!ana.isExceptionType(texc)) continue;
				/*
				CtTypeReference<?> runtimeExc = before.getFactory().Type().createReference("java.lang.RuntimeException");
				if (!TypeHelper.typeCompatible(runtimeExc, texc)) {
					boolean isThrown = false;
					CtExecutable<?> enclosingExec = ana.getEnclosingFunc();
					if (enclosingExec == null)
						continue;
					for (CtTypeReference<?> thrownT : enclosingExec.getThrownTypes()) {
						if (TypeHelper.typeCompatible(thrownT, texc)) {
							isThrown = true;
							break;
						}
					}
					if (!isThrown)
						continue;
				}*/
			}
			EnumerateGenerator tmp = new EnumerateGenerator(gen);
			tmp.eleBound --;
			if (ana.inBefore(exec))
				tmp.execBound.decInBefore();
			else if (ana.inFunc(exec))
				tmp.execBound.decInFunc();
			else if (ana.inFile(exec))
				tmp.execBound.decInFile();
			else
				tmp.execBound.decInBinding();

			ArrayList<GenRecord> tmprecs = genArgumentList(before, f, exec, tmp, gen.allowCast, ana);
			for (GenRecord r : tmprecs) {
				CtConstructorCall<Object> consCall = f.Core().createConstructorCall();
				consCall.setExecutable(exec);
				List<CtExpression<?>> args = (List<CtExpression<?>>) r.n.getRawObject();
				consCall.setArguments(args);
				if (!TypeHelper.inferType(consCall)) continue;
				GenRecord rec = new GenRecord(new MyCtNode(consCall, false));
				rec.add(r);
				rec.eleUsed ++;
				if (ana.inBefore(exec))
					rec.execUsed.incInBefore();
				else if (ana.inFunc(exec))
					rec.execUsed.incInFunc();
				else if (ana.inFile(exec))
					rec.execUsed.incInFile();
				else
					rec.execUsed.incInBinding();
				ret.add(rec);
			}
		}
		return ret;
	}

	@SuppressWarnings({ "unchecked" })
	private static ArrayList<GenRecord> genArrayAccess(MyCtNode before, Factory f,EnumerateGenerator gen,
			boolean iswrite, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (iswrite) {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtArrayWriteImpl.class, false)))
				return ret;
		}
		else {
			if (!gen.allowedSigs.contains(new MyNodeSig(CtArrayReadImpl.class, false)))
				return ret;
		}

		if (gen.eleBound <= 0) return ret;
		ArrayList<Class<?>> clazzs = new ArrayList<Class<?>>();
		clazzs.add(CtExpression.class);
		clazzs.add(CtExpression.class);
		ArrayList<ArrayList<GenRecord>> tmpl = generateImplMulti(before, f, gen.cloneWithOneLessEle(), clazzs, ana);
		for (ArrayList<GenRecord> r : tmpl) {
			GenRecord r1 = r.get(0);
			GenRecord r2 = r.get(1);
			if (!TypeHelper.isArrayType(r1.n)) continue;
			if (!TypeHelper.isIntegerType(r2.n)) continue;
			CtExpression<Object> t = (CtExpression<Object>) r1.n.getRawObject();
			CtExpression<Integer> idx = (CtExpression<Integer>) r2.n.getRawObject();
			MyCtNode n = null;
			if (iswrite) {
				CtArrayWrite<?> accw = f.Core().createArrayWrite();
				accw.setTarget(t);
				accw.setIndexExpression(idx);
				if (!TypeHelper.inferType(accw)) continue;
				n = new MyCtNode(accw, false);
			}
			else {
				CtArrayRead<?> accr = f.Core().createArrayRead();
				accr.setTarget(t);
				accr.setIndexExpression(idx);
				if (!TypeHelper.inferType(accr)) continue;
				n = new MyCtNode(accr, false);
			}
			GenRecord rec = new GenRecord(n);
			rec.add(r1);
			rec.add(r2);
			rec.eleUsed ++;
			ret.add(rec);
		}
		return ret;
	}

	private static ArrayList<GenRecord> genArrayRead(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		return genArrayAccess(before, f, gen, false, ana);
	}
	
	private static ArrayList<GenRecord> genArrayWrite(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		return genArrayAccess(before, f, gen, true, ana);
	}
	
	private static ArrayList<GenRecord> genLiteral(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtLiteralImpl.class, false)))
			return ret;
		if (gen.allowedConst.size() == 0) return ret;
		if (gen.eleBound <= 0) return ret;
		if (gen.constBound <= 0) return ret;
		for (Object c : gen.allowedConst) {
			if (Config.filterStringConstant && (c instanceof String)) {
				String str = (String) c;
				if (str.length() > 3 && (str.contains("%") || str.contains("(") || str.contains(")") || str.contains("{") || str.contains("}")))
					continue;
			}
			CtLiteral<?> lit = f.Code().createLiteral(c);
			if (!TypeHelper.inferType(lit)) continue;
			GenRecord rec = new GenRecord(new MyCtNode(lit, false));
			rec.constUsed.add(c);
			rec.eleUsed ++;
			rec.constBUsed ++;
			ret.add(rec);
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<GenRecord> genConditional(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtConditionalImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0) return ret;
		ArrayList<Class<?>> clazzs = new ArrayList<Class<?>>();
		clazzs.add(CtExpression.class);
		clazzs.add(CtExpression.class);
		clazzs.add(CtExpression.class);
		ArrayList<ArrayList<GenRecord>> tmpl = generateImplMulti(before, f, gen.cloneWithOneLessEle(), clazzs, ana);
		for (ArrayList<GenRecord> r: tmpl) {
			GenRecord r1 = r.get(0);
			GenRecord r2 = r.get(1);
			GenRecord r3 = r.get(2);
			if (!TypeHelper.isType(r1.n, "boolean"))
				continue;
			CtExpression<Boolean> condb = (CtExpression<Boolean>) r1.n.getRawObject();
			CtExpression<Object> thene = (CtExpression<Object>) r2.n.getRawObject();
			CtExpression<Object> elsee = (CtExpression<Object>) r3.n.getRawObject();
			CtConditional<Object> cond = f.Core().createConditional()
					.setCondition(condb).setThenExpression(thene).setElseExpression(elsee);
			if (!TypeHelper.inferType(cond)) continue;
			MyCtNode n = new MyCtNode(cond, false);
			GenRecord rec = new GenRecord(n);
			rec.add(r1);
			rec.add(r2);
			rec.add(r3);
			rec.eleUsed ++;
			ret.add(rec);
		}
		return ret;
	}

	private static ArrayList<GenRecord> genBinaryOperator(MyCtNode before, Factory f, EnumerateGenerator gen, StaticAnalyzer ana) {
		ArrayList<GenRecord> ret = new ArrayList<GenRecord>();
		if (!gen.allowedSigs.contains(new MyNodeSig(CtBinaryOperatorImpl.class, false)))
			return ret;
		if (gen.eleBound <= 0)
			return ret;
		ArrayList<Class<?>> clazzs = new ArrayList<Class<?>>();
		clazzs.add(CtExpression.class);
		clazzs.add(CtExpression.class);
		ArrayList<ArrayList<GenRecord>> tmpl = generateImplMulti(before, f, gen.cloneWithOneLessEle(), clazzs, ana);

		for (ArrayList<GenRecord> r : tmpl) {
			GenRecord r1 = r.get(0);
			GenRecord r2 = r.get(1);
			for (MyNodeSig sig : gen.allowedSigs) {
				if (!sig.isBinop()) continue;
				if (sig.getBinop() == BinaryOperatorKind.INSTANCEOF)
					continue;
				CtExpression<?> left = (CtExpression<?>)r1.n.getRawObject();
				CtExpression<?> right = (CtExpression<?>)r2.n.getRawObject();
				// We do not pass two same tree to the relational
				// XXX: This may cause failure in CorpusTest if we get stupid trees
				if (TypeHelper.isRelational(sig.getBinop()))
					if (r1.n.treeEquals(r2.n)) continue;
				// We do not generate trivial boolean expression with false/true
				if (sig.getBinop() == BinaryOperatorKind.AND || sig.getBinop() == BinaryOperatorKind.OR) {
					if (left instanceof CtLiteral || right instanceof CtLiteral)
						continue;
				}
				CtBinaryOperator<?> binop = f.Code().createBinaryOperator(left, right, sig.getBinop());
				if (!TypeHelper.inferType(binop)) continue;
				MyCtNode n = new MyCtNode(binop, false);
				GenRecord rec = new GenRecord(n);
				rec.add(r1);
				rec.add(r2);
				rec.eleUsed ++;
				ret.add(rec);
			}
		}
		
		if (gen.allowedSigs.contains(new MyNodeSig(BinaryOperatorKind.INSTANCEOF, false))) {
			ArrayList<GenRecord> tmpl1 = generateImpl(before, f, gen.cloneWithOneLessEle().cloneWithRootSig(CtExpression.class), ana);
			ArrayList<CtTypeReference<?>> types = ana.getTypesInFunc(); 
			for (GenRecord r : tmpl1) {
				for (CtTypeReference<?> t : types) {
					if (t.isPrimitive()) continue;
					if (t.equals(f.Type().nullType())) continue;
					CtExpression<?> left = (CtExpression<?>)r.n.getRawObject();
					try {
						if (!StaticAnalyzer.isInterface(t)) {
							if (!TypeHelper.typeCompatible(left.getType(), t))
								continue;
						}
					}
					catch (Exception e) {
						// XXX: isInterface uses getActualClass which may throw this when fail
						// I will just assume it is not interface.
					}
					if (left.getType().isPrimitive()) continue;
					if (left.getType().equals(f.Type().nullType())) continue;
					CtLiteral<CtTypeReference<?>> right = f.Core().createLiteral();
					right.setValue(t);
					CtBinaryOperator<?> binop = f.Code().createBinaryOperator(left, right, BinaryOperatorKind.INSTANCEOF);
					if (!TypeHelper.inferType(binop)) continue;
					MyCtNode n = new MyCtNode(binop, false);
					GenRecord rec = new GenRecord(n);
					rec.add(r);
					rec.eleUsed ++;
					ret.add(rec);
				}
			}
		}
		return ret;
	}

	private static ArrayList<ArrayList<GenRecord>> generateImplMulti(MyCtNode before, Factory f, EnumerateGenerator gen, List<Class<?>> clazzs, StaticAnalyzer ana) {
		assert(clazzs.size() > 0);
		if (clazzs.size() == 1) {
			ArrayList<GenRecord> tmp = generateImpl(before, f, gen.cloneWithRootSig(clazzs.get(0)), ana);
			ArrayList<ArrayList<GenRecord>> ret = new ArrayList<ArrayList<GenRecord>>();
			for (GenRecord r : tmp) {
				ArrayList<GenRecord> entry = new ArrayList<GenRecord>();
				entry.add(r);
				ret.add(entry);
			}
			return ret;
		}
		else {
			ArrayList<Class<?>> tmpclazzs = new ArrayList<Class<?>>(clazzs);
			tmpclazzs.remove(0);
			ArrayList<GenRecord> tmprecs = generateImpl(before, f, gen.cloneWithRootSig(clazzs.get(0)), ana);
			ArrayList<ArrayList<GenRecord>> ret = new ArrayList<ArrayList<GenRecord>>();
			for (GenRecord rec : tmprecs) {
				EnumerateGenerator tmp = new EnumerateGenerator(gen);
				boolean ret1 = tmp.updateWithRecord(rec);
				if (!ret1) continue;
				ArrayList<ArrayList<GenRecord>> tmpres = generateImplMulti(before, f, tmp, tmpclazzs, ana);
				for (ArrayList<GenRecord> lastpart : tmpres) {
					ArrayList<GenRecord> entry = new ArrayList<GenRecord>();
					entry.add(rec);
					entry.addAll(lastpart);
					ret.add(entry);
				}
			}
			return ret;
		}
	}

	private EnumerateGenerator cloneWithRootSig(Class<?> clazz) {
		EnumerateGenerator ret  = new EnumerateGenerator(this);
		ret.rootSig = new MyNodeSig(clazz, false);
		return ret;
	}

	private EnumerateGenerator cloneWithOneLessEle() {
		EnumerateGenerator ret = new EnumerateGenerator(this);
		ret.eleBound --;
		return ret;
	}

	@Override
	public ArrayList<MyCtNode> generate(MyCtNode before, Factory f, VarTypeContext context) {
		ArrayList<GenRecord> tmp = new ArrayList<GenRecord>();
		StaticAnalyzer ana = new StaticAnalyzer(before);
		tmp.addAll(generateImpl(before, f, this, ana));
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (allowNull)
			ret.add(new MyCtNode(null, true));
		for (GenRecord r : tmp)
			ret.add(r.n);
		return ret;
	}

	public boolean greaterEq(EnumerateGenerator a) {
		// A special case where rootSig is generated from a null value
		if (a.rootSig.getClassSig() != null && !rootSig.isAssignableFrom(a.rootSig)) return false;
		if (!allowedSigs.containsAll(a.allowedSigs)) return false;
		if (eleBound < a.eleBound) return false;
		if (a.allowCast && !allowCast) return false;
		if (!allowedConst.containsAll(a.allowedConst)) return false;
		if (!varBound.greaterEq(a.varBound)) return false;
		//if (!typeBound.greaterEq(a.typeBound)) return false;
		if (!execBound.greaterEq(a.execBound)) return false;
		if (!bindTargetBound.greaterEq(a.bindTargetBound)) return false;
		if (constBound < a.constBound) return false;
		if (a.allowNull && !allowNull) return false;
		return true;
	}

	@Override
	public boolean covers(MyCtNode before, MyCtNode tree, VarTypeContext context) {
		EnumerateGenerator tmp = createGenerator(tree, before, context);
		if (tmp == null) return false;
		return greaterEq(tmp);
	}

	@Override
	public long estimateCost(MyCtNode before) {
		// FIXME: I am going to assume the binary case, and compute leaf and inner nodes separately
		// This is a very rough estimation
		int leafnodes = eleBound;
		if (leafnodes < 1)
			leafnodes = 1;
		//if (leafnodes > varBound.sums() + execBound.sums() + constBound)
			//leafnodes = varBound.sums() + execBound.sums() + constBound;
		//int innernodes = eleBound - leafnodes;
		//if (innernodes < 0) innernodes = 0;
		
		int base = 1;
		boolean hasBinop = false;
		boolean hasUop = false;
		for (MyNodeSig sig : allowedSigs) {
			if (sig.isBinop()) hasBinop = true;
			if (sig.isUop()) hasUop = true;
			//if (sig.isClass(CtInvocation.class, false)) base ++;
		}
		if (hasBinop) base ++;
		if (hasUop) base ++;

		long ret = 1;
		
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ArrayList<CtTypeReference<?>> trefs = null;
		int num = base + ana.getNumVarRefsInFile();
		if (bindTargetBound.inBinding() > 0) {
			trefs = ana.getBindTypeInBinding();
			num += ana.getNumVarRefsInBinding(trefs);
		}
		else if (bindTargetBound.inFile() > 0) {
			trefs = ana.getBindTypeInFile();
			num += ana.getNumVarRefsInBinding(trefs);
		}
		else if (bindTargetBound.inFunc() > 0) {
			trefs = ana.getBindTypeInFunc();
			num += ana.getNumVarRefsInBinding(trefs);
		}
		else if (bindTargetBound.inBefore() > 0) {
			trefs = ana.getBindTypeInBefore();
			num += ana.getNumVarRefsInBinding(trefs);
		}
		else {
			trefs = null;
			num = 0;
		}
		if (varBound.inBinding() > 0 && num > 0) {
			if (leafnodes > varBound.inBinding()) {
				ret *= CombNo.C(leafnodes, varBound.inBinding()) *
						CombNo.intPow(num, varBound.inBinding());
				leafnodes -= varBound.inBinding();
			}
			else {
				CombNo.intPow(num, varBound.inBinding());
				leafnodes = 0;
			}
		}
		
		if (ret > Config.costlimit) return -1;
		
		if (trefs == null)
			// XXX: This won't be able to combine with others to pass type check...
			// This is a hacky number i put
			num = 2;
		else
			num = base + ana.getNumExecRefsInBinding(trefs) + ana.getNumExecRefsInFile();
		if (execBound.inBinding() > 0 && num > 0) {
			if (leafnodes > varBound.inBinding()) {
				ret *= CombNo.C(leafnodes, execBound.inBinding()) *
						CombNo.intPow(num, 
								execBound.inBinding());
				leafnodes -= execBound.inBinding();
			}
			else {
				CombNo.intPow(num, execBound.inBinding());
				leafnodes = 0;
			}
		}
		
		if (ret > Config.costlimit) return -1;
		
		num = base + ana.getNumVarRefsInFile();
		if (num > 0) {
			if (leafnodes > varBound.inFile()) {
				ret *= CombNo.C(leafnodes, varBound.inFile()) *
						CombNo.intPow(num, varBound.inFile());
				leafnodes -= varBound.inFile();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}

		if (ret > Config.costlimit) return -1;

		num = base + ana.getNumExecRefsInFile();
		if (num > 0) {
			if (leafnodes > execBound.inFile()) {
				ret *= CombNo.C(leafnodes, execBound.inFile()) *
						CombNo.intPow(num, execBound.inFile());
				leafnodes -= execBound.inFile();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}

		if (ret > Config.costlimit) return -1;

		num = base + ana.getNumVarRefsInFunc();
		if (num > 0) {
			if (leafnodes > varBound.inFunc()) {
				ret *= CombNo.C(leafnodes, varBound.inFunc()) *
						CombNo.intPow(num, varBound.inFunc());
				leafnodes -= varBound.inFunc();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}

		if (ret > Config.costlimit) return -1;

		num = base + ana.getNumExecRefsInFunc();
		if (num > 0) {
			if (leafnodes > execBound.inFunc()) {
				ret *= CombNo.C(leafnodes, execBound.inFunc()) *
						CombNo.intPow(num, execBound.inFunc());
				leafnodes -= execBound.inFunc();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}

		if (ret > Config.costlimit) return -1;
		
		// FIXME: this will probably miss a lot of counts
		if (allowedConst.size() > 0 && allowedSigs.contains(new MyNodeSig(CtLiteralImpl.class, false))) {
			int sz = allowedConst.size();
			// FIXME: this is too hacky, just to reduce by 2 
			// because many of those will not pass type check
			if (Config.presetConstants && sz > 3)
				sz -= 3;
			int pos = 0;
			if (leafnodes > constBound) {
				pos = constBound;
			}
			else {
				pos = leafnodes;
			}
			ret *= CombNo.C(leafnodes, pos) *
					CombNo.intPow(sz, pos);
			leafnodes -= pos;
		}
		
		if (ret > Config.costlimit) return -1;
		
		num = base + ana.getNumVarRefsInBefore();

		if (num > 0) {
			if (leafnodes > varBound.inBefore()) {
				ret *= CombNo.C(leafnodes, varBound.inBefore()) *
						CombNo.intPow(num, varBound.inBefore());
				leafnodes -= varBound.inBefore();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}

		if (ret > Config.costlimit) return -1;
		num = base + ana.getNumExecRefsInBefore();

		if (num > 0) {
			if (leafnodes > execBound.inBefore()) {
				ret *= CombNo.C(leafnodes, execBound.inBefore()) *
						CombNo.intPow(num, execBound.inBefore());
				leafnodes -= execBound.inBefore();
			}
			else {
				ret *= CombNo.intPow(num, leafnodes);
				leafnodes = 0;
			}
		}
		
		if (leafnodes > 0) {
			ret *= CombNo.intPow(base, leafnodes);
		}
		
		// FIXME: This is purely empirical numbers to reduce the skew
		// at high number of elements
		if (eleBound > 2) {
			if (!rootSig.isCollection())
				ret /= 2 + (eleBound - 3) * 3;
		}
		
		if (ret > Config.costlimit) return -1;

		// we are just going to estimate the base for inner nodes
		/*int base = 1;
		for (MyNodeSig sig : allowedSigs) {
			if (sig.isBinop()) base ++;
			//if (sig.isClass(CtInvocation.class, false)) base ++;
		}
		ret *= CombNo.intPow(base, innernodes);

		if (ret > Config.costlimit) return -1;*/

		if (allowNull) ret += 1;
		
		return ret;
	}
	
	public boolean getAllowCast() {
		return allowCast;
	}
}
