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

import genesis.analysis.StaticAnalyzer;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.VarTypeContext;
import genesis.utils.StringUtil;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.reflect.reference.CtExecutableReferenceImpl;
import spoon.support.reflect.reference.CtFieldReferenceImpl;
import spoon.support.reflect.reference.CtLocalVariableReferenceImpl;
import spoon.support.reflect.reference.CtParameterReferenceImpl;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

public class ReferenceGenerator extends VarGenerator implements Serializable {
	
	public static enum ReferenceScopeKind {
		NONE(0),
		BEFORE(1),
		FUNC(2),
		FILE(3),
		BINDING(4);
		
		int id;
		
		ReferenceScopeKind(int id) {
			this.id = id;
		}
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private MyNodeSig rootSig;
	private ReferenceScopeKind execScope, varScope, typeScope;
	private String nameContains;
	private boolean nameExist;
	
	static private Set<String> commonExceptions;
	
	static {
		commonExceptions = new HashSet<String>();
		//commonExceptions.add("java.lang.RuntimeException");
		commonExceptions.add("java.lang.ClassCastException");
		commonExceptions.add("java.lang.NullPointerException");
		commonExceptions.add("java.lang.IndexOutOfBoundsException");
		commonExceptions.add("java.lang.StringIndexOutOfBoundsException");
		commonExceptions.add("java.lang.Exception");
	}
	
	public ReferenceGenerator() {
		rootSig = null;
		execScope = ReferenceScopeKind.NONE;
		varScope = ReferenceScopeKind.NONE;
		typeScope = ReferenceScopeKind.NONE;
		nameContains = null;
		nameExist = false;
	}
	
	public ReferenceGenerator(MyNodeSig rootSig, ReferenceScopeKind execScope, 
			ReferenceScopeKind varScope, ReferenceScopeKind typeScope, String nameContains, boolean nameExist) {
		this.rootSig = rootSig;
		this.execScope = execScope;
		this.varScope = varScope;
		this.typeScope = typeScope;
		this.nameContains = nameContains;
		this.nameExist = nameExist;
	}
	
	protected static ReferenceGenerator createGenerator(MyCtNode tree, MyCtNode before, VarTypeContext typeContext) {
		// XXX: We do not handle a list of CtTypeReference for now
		// We may want to create a new generator to handle this
		if (tree.nodeSig().isClass(CtTypeReference.class, true))
			return null;
		if (!tree.isReference())
			return null;
		
		ReferenceGenerator ret = new ReferenceGenerator();
		CtReference n = (CtReference) tree.getRawObject();
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ret.rootSig = tree.nodeSig();
		if (n instanceof CtTypeReference) {
			CtTypeReference<?> tr = (CtTypeReference<?>)n;
			if (ana.inBeforeType(tr)) {
				ret.typeScope = ReferenceScopeKind.BEFORE;
			}
			else if (ana.inFuncType(tr) || isCommonException(tr)) {
				// XXX: for now i am going to encode common exceptions at this level
				ret.typeScope = ReferenceScopeKind.FUNC;
			}
			else if (ana.inFileType(tr)) {
				ret.typeScope = ReferenceScopeKind.FILE;
			}
			else
				return null;
		}
		else {
			if (typeContext == null)
				typeContext = new VarTypeContext();
			if (n instanceof CtFieldReference) 
				ret.nameExist = ana.isFieldNameExist(n.getSimpleName());
			else if (n instanceof CtExecutableReference)
				ret.nameExist = ana.isExecNameExist(n.getSimpleName());
			
			boolean enableBindingRef = typeContext.exceptionOnly || (typeContext.targetType != null) || ret.nameExist;
			
			// A special case where we cannot determine the previous targeted typeref in the tree
			// We just mark it failed.
			if (!enableBindingRef && !ana.inFile(n)) 
				return null;
			if (n instanceof CtVariableReference) {
				CtVariableReference<?> vn = (CtVariableReference<?>) n;
				if (ana.inBefore(vn))
					ret.varScope = ReferenceScopeKind.BEFORE;
				else if (ana.inFunc(vn))
					ret.varScope = ReferenceScopeKind.FUNC;
				else if (ana.inFile(vn))
					ret.varScope = ReferenceScopeKind.FILE;
				else if (ana.inBinding(vn)) {
					ret.varScope = ReferenceScopeKind.BINDING;
				}
				else {
					return null;
				}
			}
			else if (n instanceof CtExecutableReference) {
				CtExecutableReference<?> enclosingFunc = ana.getEnclosingFuncReference();
				// XXX: We are not going to replace constructor reference
				// This may occur for super(), which we do not handle
				if (((CtExecutableReference<?>) n).isConstructor() && enclosingFunc != null && enclosingFunc.isConstructor()) {
					return null;
				}
				
				CtExecutableReference<?> en = (CtExecutableReference<?>) n;
				
				if (ana.inBefore(en))
					ret.execScope = ReferenceScopeKind.BEFORE;
				else if (ana.inFunc(en))
					ret.execScope = ReferenceScopeKind.FUNC;
				else if (ana.inFile(en))
					ret.execScope = ReferenceScopeKind.FILE;
				else if (ana.inBinding(en)) {
					ret.execScope = ReferenceScopeKind.BINDING;
				}
				else
					return null;
			}
		}
		
		ret.nameContains = n.getSimpleName();
		
		return ret;
	}

	public ReferenceGenerator mergeWith(ReferenceGenerator a) {
		ReferenceGenerator ret = new ReferenceGenerator();
		ret.rootSig = rootSig.getCommonSuperSig(a.rootSig);
		ret.varScope = varScope.id > a.varScope.id ? varScope : a.varScope;
		ret.execScope = execScope.id > a.execScope.id ? execScope : a.execScope;
		ret.typeScope = typeScope.id > a.typeScope.id ? typeScope : a.typeScope;
		ret.nameContains = StringUtil.getLongestCommonSubStr(nameContains, a.nameContains);
		if (ret.nameContains.length() < 3)
			ret.nameContains = "";
		ret.nameExist = nameExist && a.nameExist;
		return ret;
	}

	public static ReferenceGenerator createGenerator(List<MyCtNode> trees, List<MyCtNode> befores, List<VarTypeContext> contexts) {
		assert(trees.size() > 0);
		ReferenceGenerator ret = createGenerator(trees.get(0), befores.get(0), contexts.get(0));
		if (ret == null)
			return null;
		for (int i = 1; i < trees.size(); i++) {
			ReferenceGenerator tmp = createGenerator(trees.get(i), befores.get(i), contexts.get(i));
			if (tmp == null)
				return null;
			ret = ret.mergeWith(tmp);
		}
		
		return ret;
	}
	
	private ArrayList<MyCtNode> genExecutableReference(MyCtNode before, Factory f,
			VarTypeContext context) {
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (execScope == null || execScope == ReferenceScopeKind.NONE) return ret;
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ArrayList<CtExecutableReference<Object>> candidates = null;
		if (execScope == ReferenceScopeKind.BINDING) {
			candidates = ana.getMethodInFile(f);
			if (context.targetType != null)
				candidates.addAll(ana.getMethodInBinding(new ArrayList<CtTypeReference<?>>(Arrays.asList(context.targetType))));
			if (context.exceptionOnly)
				candidates.addAll(ana.getExceptionConstructorInBinding());
			if (nameExist == true)
				candidates.addAll(ana.getMethodInBinding(ana.getBindTypeInBinding()));
		}
		else if (execScope == ReferenceScopeKind.FILE) {
			if (context.constructorOnly) 
				candidates = ana.getConstructorInFile(f);
			else
				candidates = ana.getMethodInFile(f);
		}
		else if (execScope == ReferenceScopeKind.FUNC) {
			if (context.constructorOnly)
				candidates = ana.getConstructorInFunc(f);
			else
				candidates = ana.getMethodInFunc(f);
		}
		else {
			if (context.constructorOnly)
				candidates = ana.getConstructorInBefore(f);
			else
				candidates = ana.getMethodInBefore(f);
		}

		for (CtExecutableReference<Object> eref : candidates) {
			if (!nameContains.equals(""))
				if (!eref.getSimpleName().contains(nameContains))
					continue;
			if (nameExist)
				if (!ana.isExecNameExist(eref.getSimpleName()))
					continue;
			if (context.constructorOnly)
				if (!eref.isConstructor())
					continue;
			if (context.exceptionOnly) {
				if (!ana.isExceptionType(eref.getDeclaringType()))
					continue;
				/*
				if (!eref.getDeclaringType().getSimpleName().contains("Exception") ||
					!eref.getDeclaringType().getSimpleName().contains("Error"))
					continue;*/
			}
			ret.add(new MyCtNode(eref, false));
		}
		return ret;
	}

	@SuppressWarnings({ "unchecked" })
	private ArrayList<MyCtNode> genFieldReference(MyCtNode before, Factory f,
			VarTypeContext context) {
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (varScope == null || varScope == ReferenceScopeKind.NONE) return ret;
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ArrayList<CtFieldReference<Object>> candidates = null;
		if (varScope == ReferenceScopeKind.BINDING) {
			candidates = ana.getFieldInFile(f);
			if (context.targetType != null)
				candidates.addAll(ana.getFieldInBinding(new ArrayList<CtTypeReference<?>>(Arrays.asList(context.targetType))));
			if (nameExist)
				candidates.addAll(ana.getFieldInBinding(ana.getBindTypeInBinding()));
		}
		else if (varScope == ReferenceScopeKind.FILE)
			candidates = ana.getFieldInFile(f);
		else if (varScope == ReferenceScopeKind.FUNC)
			candidates = ana.getFieldInFunc(f);
		else
			candidates = ana.getFieldInBefore(f);

		for (CtVariableReference<Object> vref : candidates) {
			if (!nameContains.equals(""))
				if (!vref.getSimpleName().contains(nameContains))
					continue;
			if (nameExist)
				if (!ana.isFieldNameExist(vref.getSimpleName()))
					continue;
			ret.add(new MyCtNode(vref, false));
		}
		return ret;
	}

	private ArrayList<MyCtNode> genVariableReference(MyCtNode before, Factory f) {
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (varScope == null || varScope == ReferenceScopeKind.NONE) return ret;
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ArrayList<CtVariableReference<Object>> candidates = null;
		if (varScope == ReferenceScopeKind.BINDING || varScope == ReferenceScopeKind.FILE)
			candidates = ana.getVarInFile(f);
		else if (varScope == ReferenceScopeKind.FUNC)
			candidates = ana.getVarInFunc(f);
		else
			candidates = ana.getVarInBefore(f);

		for (CtVariableReference<Object> vref : candidates) {
			// There are two possible signatures for variables,
			// CtParameterReferenceImpl or CtLocalVariableReferenceImpl,
			// but I am not going to distinguish these two
			//if (!rootSig.isAssignableFrom(vref.getClass())) continue;
			if (!nameContains.equals(""))
				if (!vref.getSimpleName().contains(nameContains))
					continue;
			ret.add(new MyCtNode(vref, false));
		}
		return ret;
	}
	
	private ArrayList<MyCtNode> genTypeReference(MyCtNode before, Factory f) {
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (typeScope == null || typeScope == ReferenceScopeKind.NONE) return ret;
		StaticAnalyzer ana = new StaticAnalyzer(before);
		ArrayList<CtTypeReference<?>> candidates = null;
		if (typeScope == ReferenceScopeKind.BINDING || typeScope == ReferenceScopeKind.FILE)
			candidates = ana.getTypesInFile();
		else if (typeScope == ReferenceScopeKind.FUNC)
			candidates = ana.getTypesInFunc();
		else
			candidates = ana.getTypesInBefore();
		if (typeScope.id > ReferenceScopeKind.BEFORE.id)
			candidates.addAll(commonExceptionTypes(f));
		for (CtTypeReference<?> tref : candidates) {
			if (!nameContains.equals(""))
				if (!tref.getSimpleName().contains(nameContains))
					continue;
			ret.add(new MyCtNode(tref, false));
		}
		return ret;
	}
	
	private static boolean isCommonException(CtTypeReference<?> tr) {
		return commonExceptions.contains(tr.getQualifiedName());
	}
	
	private List<CtTypeReference<?>> commonExceptionTypes(Factory f) {
		ArrayList<CtTypeReference<?>> ret = new ArrayList<CtTypeReference<?>>();
		for (String e : commonExceptions) {
			ret.add(f.Type().createReference(e));
		}
		return ret;
	}

	@Override
	public ArrayList<MyCtNode> generate(MyCtNode before, Factory f, VarTypeContext context) {
		if (context == null)
			context = new VarTypeContext();
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		if (rootSig.isAssignableFrom(CtLocalVariableReferenceImpl.class) ||
				rootSig.isAssignableFrom(CtParameterReferenceImpl.class))
			ret.addAll(genVariableReference(before, f));
		if (rootSig.isAssignableFrom(CtFieldReferenceImpl.class))
			ret.addAll(genFieldReference(before, f, context));
		if (rootSig.isAssignableFrom(CtExecutableReferenceImpl.class))
			ret.addAll(genExecutableReference(before, f, context));
		if (rootSig.isAssignableFrom(CtTypeReferenceImpl.class))
			ret.addAll(genTypeReference(before, f));
		return ret;
	}

	@Override
	public boolean covers(MyCtNode before, MyCtNode tree, VarTypeContext context) {
		ReferenceGenerator tmp = createGenerator(tree, before, context);
		if (tmp == null) return false;
		return greaterEq(tmp);
	}

	private boolean greaterEq(ReferenceGenerator a) {
		// XXX: so far we do not distinguish these two in our current version
		boolean pass = false;
		if (rootSig.isClass(CtParameterReferenceImpl.class, false) &&
			a.rootSig.isClass(CtLocalVariableReferenceImpl.class, false))
			pass = true;
		if (rootSig.isClass(CtLocalVariableReferenceImpl.class, false) &&
			a.rootSig.isClass(CtParameterReferenceImpl.class, false))
			pass = true;
		if (!rootSig.isAssignableFrom(a.rootSig) && !pass) return false;
		if (varScope.id < a.varScope.id) return false;
		if (execScope.id < a.execScope.id) return false;
		if (typeScope.id < a.typeScope.id) return false;
		if (!a.nameContains.contains(nameContains)) return false;
		if (nameExist && !a.nameExist) return false;
		return true;
	}
	
	@Override
	public long estimateCost(MyCtNode before) {
		StaticAnalyzer ana = new StaticAnalyzer(before);
		long ret = 0;
		if (varScope == ReferenceScopeKind.BINDING) {
			ret += ana.getNumVarRefsInBinding(ana.getBindTypeInBefore()) + ana.getNumVarRefsInFile();
		}
		else if (varScope == ReferenceScopeKind.FILE)
			ret += ana.getNumVarRefsInFile();
		else if (varScope == ReferenceScopeKind.FUNC)
			ret += ana.getNumVarRefsInFunc();
		else if (varScope == ReferenceScopeKind.BEFORE)
			ret += ana.getNumVarRefsInBefore();
		
		if (execScope == ReferenceScopeKind.BINDING) {
			ret += ana.getNumExecRefsInBinding(ana.getBindTypeInBefore()) + ana.getNumExecRefsInFile();
		}
		else if (execScope == ReferenceScopeKind.FILE)
			ret += ana.getNumExecRefsInFile();
		else if (execScope == ReferenceScopeKind.FUNC)
			ret += ana.getNumExecRefsInFunc();
		else if (execScope == ReferenceScopeKind.BEFORE)
			ret += ana.getNumExecRefsInBefore();
		
		if (typeScope == ReferenceScopeKind.BINDING || typeScope == ReferenceScopeKind.FILE)
			ret += ana.getNumTypesInFile();
		else if (typeScope == ReferenceScopeKind.FUNC)
			ret += ana.getNumTypesInFunc();
		else
			ret += ana.getNumTypesInBefore();
		
		// XXX: put it back to a small constant, maybe we really need to change interface and
		// just use the real number here
		if (!nameContains.equals(""))
			if (ret > 5)
				ret = 5;
		if (nameExist)
			if (ret > 10)
				ret = 10;
		
		return ret;
	}

	@Override
	public boolean generatorEquals(VarGenerator g) {
		if (!(g instanceof VarGenerator)) return false;
		ReferenceGenerator a = (ReferenceGenerator) g;
		if (!rootSig.equals(a.rootSig)) return false;
		if (varScope != a.varScope) return false;
		if (execScope != a.execScope) return false;
		if (typeScope != a.typeScope) return false;
		if (!nameContains.equals(a.nameContains)) return false;
		if (nameExist != a.nameExist) return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("rootsig: " + rootSig + "\n");
		ret.append("VarScope: " + varScope + "\n");
		ret.append("ExecScope: " + execScope + "\n");
		ret.append("TypeScope: " + typeScope + "\n");
		ret.append("NameContains: \"" + nameContains + "\"\n");
		ret.append("NameExist: \"" + nameExist + "\"\n");
		return ret.toString();
	}
}
