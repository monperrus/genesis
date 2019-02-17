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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import genesis.Config;
import genesis.GenesisException;
import genesis.analysis.StaticAnalyzer;
import genesis.analysis.StatementAnalyzer;
import genesis.analysis.TypeHelper;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.VarTypeContext;
import genesis.schema.TransASTCollection;
import genesis.utils.CombNo;
import genesis.utils.Pair;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtCatchVariableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.util.RtHelper;

public class CopyGenerator extends VarGenerator implements Serializable {

	private MyNodeSig rootSig;
	private int scope; // 0 inFunc, or 1 inPackage
	private int maxLen;
	private RefBound varBound;

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	// This is for serialization
	private CopyGenerator() {
		this.rootSig = null;
		this.scope = 0;
		this.maxLen = 0;
		this.varBound = null;
	}

	private CopyGenerator(CopyGenerator a) {
		this.rootSig = a.rootSig;
		this.scope = a.scope;
		this.maxLen = a.maxLen;
		this.varBound = new RefBound(a.varBound);
	}

	public static ArrayList<CopyGenerator> createGenerators(List<MyCtNode> trees, List<MyCtNode> befores) {
		assert(trees.size() > 0);
		assert(trees.size() == befores.size());
		ArrayList<CopyGenerator> retl = new ArrayList<CopyGenerator>();
		Pair<CopyGenerator, CopyGenerator> ret = createGenerator(trees.get(0), befores.get(0));
		if (ret.x == null && ret.y == null) return retl;
		for (int i = 1; i < trees.size(); i++) {
			Pair<CopyGenerator, CopyGenerator> tmp = createGenerator(trees.get(i), befores.get(i));
			if (tmp.x == null)
				ret.x = null;
			else if (ret.x != null)
				ret.x = ret.x.mergeWith(tmp.x);
			if (tmp.y == null)
				ret.y = null;
			else if (ret.y != null)
				ret.y = ret.y.mergeWith(tmp.y);
			if (ret.x == null && ret.y == null) return retl;
		}
		if (ret.x != null)
			retl.add(ret.x);
		if (ret.y != null) {
			if (ret.x == null || (!ret.y.greaterEq(ret.x)))
				retl.add(ret.y);
		}
		return retl;
	}

	private boolean greaterEq(CopyGenerator a) {
		if (!rootSig.isAssignableFrom(a.rootSig)) return false;
		if (scope < a.scope) return false;
		if (maxLen < a.maxLen) return false;
		return varBound.greaterEq(a.varBound);
	}

	private CopyGenerator mergeWith(CopyGenerator a) {
		if (a == null) return null;
		CopyGenerator ret = new CopyGenerator(this);
		ret.rootSig = rootSig.getCommonSuperSig(a.rootSig);
		if (ret.scope < a.scope)
			ret.scope = a.scope;
		if (ret.maxLen < a.maxLen)
			ret.maxLen = a.maxLen;
		ret.varBound = ret.varBound.mergeWith(a.varBound);
		return ret;
	}

	private static Pair<CopyGenerator, CopyGenerator> createGenerator(MyCtNode tree, MyCtNode before) {
		MyNodeSig sig = tree.nodeSig();
		if (tree.isCollection()) {
			if (!sig.isClass(CtStatement.class, true)) return new Pair<CopyGenerator, CopyGenerator>(null, null);
		}
		else {
			if (!sig.isClass(CtStatement.class, false)) return new Pair<CopyGenerator, CopyGenerator>(null, null);
		}
		Pair<CopyGenerator, CopyGenerator> ret = new Pair<CopyGenerator, CopyGenerator>(
				new CopyGenerator(), new CopyGenerator());
		ret.x.rootSig = ret.y.rootSig = tree.nodeSig();
		ret.x.maxLen = ret.y.maxLen = 1;
		ret.x.scope = 0;
		ret.y.scope = 1;
		if (tree.isCollection())
			ret.x.maxLen = ret.y.maxLen = tree.getNumChildren();
		StatementAnalyzer ana = new StatementAnalyzer(before);
		ArrayList<MyCtNode> stmts = ana.getCandidateInFunc(ret.x.rootSig, ret.x.maxLen);
		ret.x.varBound = computeVarBound(stmts, tree, before, new RefBound(100, 100, 100, 100));
		stmts = ana.getCandidateInPackage(ret.x.rootSig, ret.x.maxLen);
		ret.y.varBound = computeVarBound(stmts, tree, before, new RefBound(100, 100, 100, 100));

		if (ret.x.varBound.inBinding() > 99)
			ret.x = null;
		if (ret.y.varBound.inBinding() > 99)
			ret.y = null;

		return ret;
	}

	private static RefBound computeVarBound(ArrayList<MyCtNode> stmts, MyCtNode tree, MyCtNode before,
			RefBound b) {
		RefBound ret = new RefBound(b);
		StaticAnalyzer ana = new StaticAnalyzer(before);
		for (MyCtNode stmt : stmts) {
			RefBound vB = diffWithRefReplace(stmt, tree, ana);
			if (vB != null)
				if (ret.greaterEq(vB)) {
					ret = vB;
				}
		}
		return ret;
	}

	private static RefBound diffWithRefReplace(MyCtNode stmt, MyCtNode tree, StaticAnalyzer ana) {
		if (stmt.treeEquals(tree)) {
			return new RefBound(0, 0, 0, 0);
		}
		if (stmt.nodeEquals(tree)) {
			RefBound ret = new RefBound(0, 0, 0, 0);
			int n = stmt.getNumChildren();
			for (int i = 0; i < n; i++) {
				RefBound tmp = diffWithRefReplace(stmt.getChild(i), tree.getChild(i), ana);
				if (tmp == null) return null;
				ret.add(tmp);
			}
			return ret;
		}
		else if (stmt.isEleClass(CtVariableReference.class) && tree.isEleClass(CtVariableReference.class)) {
			CtVariableReference<?> var = (CtVariableReference<?>)tree.getRawObject();
			if (ana.inBefore(var))
				return new RefBound(1, 0, 0, 0);
			else if (ana.inFunc(var))
				return new RefBound(0, 1, 0, 0);
			else if (ana.inFile(var))
				return new RefBound(0, 0, 1, 0);
			else
				return null;
		}
		else
			return null;
	}

	@SuppressWarnings("unchecked")
	private ArrayList<MyCtNode> replaceVarsIn(MyCtNode stmt, RefBound b, StaticAnalyzer ana, Factory f) {
		ArrayList<CtVariableReference<?>> varsInPackage = ana.getVarRefsInFile(f);
		ArrayList<CtVariableReference<?>> varsInFunc = ana.getVarRefsInFunc(f);
		ArrayList<CtVariableReference<?>> varsInTree = ana.getVarRefsInBefore(f);

		ArrayList<CtVariableReference<?>> vars = new ArrayList<CtVariableReference<?>>();
		int m1 = 0, m2 = 0;
		for (CtVariableReference<?> v : varsInPackage) {
			if (!ana.inFunc(v)) {
				vars.add(v);
				m1 ++;
			}
		}
		for (CtVariableReference<?> v : varsInFunc) {
			if (!ana.inBefore(v)) {
				vars.add(v);
				m2 ++;
			}
		}
		vars.addAll(varsInTree);

		ArrayList<CtVariableReference<?>> refsInStmt = computeRefsInStmt(stmt, f);
		int n = refsInStmt.size();
		ArrayList<CtVariableReference<?>> mustReplaceRefs = new ArrayList<CtVariableReference<?>>(); 
		final HashSet<String> tmpSet = new HashSet<String>();
		for (CtVariableReference<?> vref : varsInPackage)
			tmpSet.add(vref.getSimpleName());
		
		CtScanner locVarScanner = new CtScanner() {
			@Override
			public void enter(CtElement ele) {
				if (ele instanceof CtLocalVariable)
					tmpSet.add(((CtLocalVariable<?>) ele).getSimpleName());
			}
		};
		stmt.acceptScanner(locVarScanner);
		
		for (CtVariableReference<?> vref : refsInStmt) {
			if ((vref instanceof CtLocalVariableReference) || (vref instanceof CtParameterReference) || 
					(vref instanceof CtCatchVariableReference))
				if (!ana.checkValid(f, (CtVariableReference<Object>) vref)) {
					// See whether it can luckily match the name string
					if (!tmpSet.contains(vref.getSimpleName()))
						mustReplaceRefs.add(vref);
				}
		}

		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		HashMap<CtVariableReference<?>, CtVariableReference<?>> M =
				new HashMap<CtVariableReference<?>, CtVariableReference<?>>();
		HashMap<CtFieldReference<?>, CtFieldReference<?>> Mf =
				new HashMap<CtFieldReference<?>, CtFieldReference<?>>();
		replaceVarsImpl(0, n, m1, m2, stmt, b, vars, refsInStmt, mustReplaceRefs, M, Mf, ret);

		return ret;
	}

	private ArrayList<CtVariableReference<?>> computeRefsInStmt(MyCtNode stmt, Factory f) {
		StaticAnalyzer ana = new StaticAnalyzer(stmt);
		return ana.getVarRefsInBefore(f);
	}

	private MyCtNode cloneAndReplaceRefs(MyCtNode stmt, final HashMap<CtVariableReference<?>, CtVariableReference<?>> m,
			final HashMap<CtFieldReference<?>, CtFieldReference<?>> mf) {

		CtScanner s = new CtScanner() {
			@Override
			public void exit(CtElement ele) {
				for (Field f : RtHelper.getAllFields(ele.getClass())) {
					f.setAccessible(true);
					try {
						Object o = f.get(ele);
						if (o instanceof CtVariableReference) {
							if (o instanceof CtFieldReference) {
								if (mf.containsKey(o))
									f.set(ele, mf.get(o));
							}
							else {
								if (m.containsKey(o)) {
									f.set(ele, m.get(o));
								}
							}
						}
					}
					catch (IllegalAccessException e) {
						e.printStackTrace();
						throw new GenesisException("Access exception in CopyGenerator.cloneAndReplaceRefs()!");
					}
				}
			}
		};

		MyCtNode ret = stmt.deepClone();
		ret.acceptScanner(s);
		ret.invalidateChildrenCache();
		return ret;
	}

	private void replaceVarsImpl(int k, int n, int m1, int m2, MyCtNode stmt, RefBound b,
			ArrayList<CtVariableReference<?>> vars, ArrayList<CtVariableReference<?>> refsInStmt,
			ArrayList<CtVariableReference<?>> mustReplaceRefs,
			HashMap<CtVariableReference<?>, CtVariableReference<?>> M, 
			HashMap<CtFieldReference<?>, CtFieldReference<?>> Mf,
			ArrayList<MyCtNode> ret) {
		if ((n == 0) || (k == vars.size())) {
			// If some invalid reference is not replaced, just give up
			for (CtVariableReference<?> vref : mustReplaceRefs) {
				if (!M.containsKey(vref)) {
					return;
				}
			}
			MyCtNode res = cloneAndReplaceRefs(stmt, M, Mf);
			ret.add(res);
			return;
		}
		boolean canpick = true;
		if (k < m1)
			canpick = (b.inFile() > 0);
		else if (k < m2)
			canpick = (b.inFile() > 0) || (b.inFunc() > 0);
		else
			canpick = !(b.isZero());
		if (canpick) {
			RefBound newb = new RefBound(b);
			if (k < m1)
				newb.decInFile();
			else if (k < m2)
				newb.decInFunc();
			else
				newb.decInBefore();
			CtVariableReference<?> r1 = vars.get(k);
			for (CtVariableReference<?> r : refsInStmt) {
				if (M.containsKey(r)) continue;
				if (!TypeHelper.typeCompatible(r.getType(), r1.getType())) continue;
				if (r instanceof CtFieldReference) {
					if (!(r1 instanceof CtFieldReference)) continue;
					CtFieldReference<?> fr = (CtFieldReference<?>) r;
					CtFieldReference<?> fr1 = (CtFieldReference<?>) r1;
					// This is tricky, we need the new field to be at the least super class
					if (!TypeHelper.typeCompatible(fr1.getDeclaringType(), fr.getDeclaringType())) continue;
				}
				else {
					if (r1 instanceof CtFieldReference) continue;
				}
				// We are not going to do self-replacement
				if (r.getSimpleName().equals(r1.getSimpleName())) continue;
				// We saperate field/varaible because spoon has a shitty bug 
				// to distinguish reference with same name
				if (r instanceof CtFieldReference)
					Mf.put((CtFieldReference<?>)r, (CtFieldReference<?>)r1);
				else
					M.put(r, r1);
				replaceVarsImpl(k + 1, n, m1, m2, stmt, newb, vars, refsInStmt, mustReplaceRefs, M, Mf, ret);
				if (r instanceof CtFieldReference)
					Mf.remove((CtFieldReference<?>)r);
				else
					M.remove(r);
			}
		}
		replaceVarsImpl(k + 1, n, m1, m2, stmt, b, vars, refsInStmt, mustReplaceRefs, M, Mf, ret);
	}

	@Override
	public ArrayList<MyCtNode> generate(MyCtNode before, Factory f, VarTypeContext context) {
		StatementAnalyzer ana = new StatementAnalyzer(before);
		ArrayList<MyCtNode> stmts;
		if (scope == 0)
			stmts = ana.getCandidateInFunc(rootSig, maxLen);
		else
			stmts = ana.getCandidateInPackage(rootSig, maxLen);
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		StaticAnalyzer rana = new StaticAnalyzer(before);
		for (MyCtNode stmt : stmts) {
			ArrayList<MyCtNode> tmp = replaceVarsIn(stmt, varBound, rana, f);
			ret.addAll(tmp);
		}
		return ret.stream()
		          .filter(x -> checkStmt(before, f, rana, context, x))
		          .collect(Collectors.toCollection(ArrayList::new));
	}

	/* Check that a statement can be used in a given context. */
	@SuppressWarnings("rawtypes")
	private boolean checkStmt(MyCtNode before,
							  Factory f,
							  StaticAnalyzer ana,
							  VarTypeContext context,
							  MyCtNode n) {
		if (n.nodeSig().isClass(CtReturn.class, false)) {
			CtTypeReference<?> tRef = ana.getEnclosingFuncReturnType();
			if (tRef == null || tRef.equals(f.Type().VOID_PRIMITIVE) || tRef.equals(f.Type().VOID)) {
				return n.getChild(0).isNull();
			} else {
				return TypeHelper.isCompatibleType(tRef, n.getChild(0));
			}
		}
		if (n.nodeSig().isClass(CtInvocation.class, false)) {
			CtInvocation ele = (CtInvocation) n.getRawObject();
			if (ele.getExecutable().isConstructor()) {
				CtType parentType;
				try {
					parentType = ele.getParent(CtType.class);
				} catch (ParentNotInitializedException e) {
					parentType = null;
				}
				if (parentType == null ||
					parentType.getQualifiedName() == null ||
					!parentType.getQualifiedName().equals(ele.getExecutable().getDeclaringType().getQualifiedName())) {
					if (context.parent instanceof TransASTCollection) {
						TransASTCollection parentEle =
							(TransASTCollection)context.parent;
						return (parentEle.nodeSig().isClass(CtConstructor.class, false) &&
								parentEle.getChild(0).equals(context.n));
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean covers(MyCtNode before, MyCtNode after, VarTypeContext context) {
		Pair<CopyGenerator, CopyGenerator> tmp = createGenerator(after, before);
		if (tmp.x != null)
			if (greaterEq(tmp.x)) return true;
		if (tmp.y != null)
			if (greaterEq(tmp.y)) return true;
		return false;
	}

	private static final int sizeEstimate = 10;

	@Override
	public long estimateCost(MyCtNode before) {
		// FIXME: This is going to be a very rough estimate of the cost
		StatementAnalyzer ana = new StatementAnalyzer(before);
		ArrayList<MyCtNode> stmts;
		if (scope == 0)
			stmts = ana.getCandidateInFunc(rootSig, maxLen);
		else
			stmts = ana.getCandidateInPackage(rootSig, maxLen);

		StaticAnalyzer rana = new StaticAnalyzer(before);

		// Going to assume that only half can put into the current context
		long ret = stmts.size() / 2;
		if (ret < 1) ret = 1;
		int tot = varBound.inFile() + varBound.inFunc() + varBound.inBefore();
		if (tot > 0) {
			int pos = sizeEstimate;
			if (2*tot > pos)
				pos = 2*tot;
			ret *= CombNo.C(pos, tot) * CombNo.factor(tot);
		}
		
		if (ret > Config.costlimit) return -1;
		
		if (varBound.inFile() > 0) {
			ret *= CombNo.intPow(rana.getNumVarRefsInFile(), varBound.inFile());
		}
		if (ret > Config.costlimit) return -1;

		if (varBound.inFunc() > 0) {
			ret *= CombNo.intPow(rana.getNumVarRefsInFunc(), varBound.inFunc());
		}
		if (ret > Config.costlimit) return -1;

		if (varBound.inBefore() > 0) {
			ret *= CombNo.intPow(rana.getNumVarRefsInBefore(), varBound.inBefore());
		}
		if (ret > Config.costlimit) return -1;

		return ret;
	}

	@Override
	public boolean generatorEquals(VarGenerator g) {
		if (!(g instanceof CopyGenerator)) return false;
		CopyGenerator a  = (CopyGenerator) g;
		if (!rootSig.equals(a.rootSig)) return false;
		if (maxLen != a.maxLen) return false;
		if (scope != a.scope) return false;
		return varBound.equals(a.varBound);
	}

	@Override
	public String toString() {
		String ret = "rootSig: " + rootSig.toString() + "\n";
		ret += "scope: " + Integer.toString(scope) + "\n";
		ret += "maxLen: " + Integer.toString(maxLen) + "\n";
		if (varBound != null)
			ret += "varBound: " + varBound.toString() + "\n";
		return ret;
	}
}
