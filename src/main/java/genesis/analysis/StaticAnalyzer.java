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
package genesis.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import genesis.Config;
import genesis.GenesisException;
import genesis.node.MyCtNode;
import genesis.utils.Unit;
import spoon.reflect.binding.CtFieldBinding;
import spoon.reflect.binding.CtMethodBinding;
import spoon.reflect.binding.CtTypeBinding;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtCatchVariableImpl;

public class StaticAnalyzer {
	
	private static LoadingCache<MyCtNode, ReferenceAnalyzerImpl> impls;
	private MyCtNode parentPackage;
	private MyCtNode parentFunc;
	private MyCtNode tree;
	private ReferenceAnalyzerImpl parentPackImpl, parentFuncImpl, treeImpl; 
	private Set<CtElement> scopeHelper;
	private HashSet<String> enclosingLabels;

	private static LoadingCache<CtVariableReference<?>, Unit<CtVariable<?>>> varDeclCache;
	private static LoadingCache<CtTypeReference<?>, Unit<CtType<?>>> typeDeclCache;
	private static LoadingCache<CtPackage, Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>>> exceptionCache;
	private static LoadingCache<CtTypeReference<?>, Boolean> isInterfaceCache;

	static {
		impls = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<MyCtNode, ReferenceAnalyzerImpl>() {
						public ReferenceAnalyzerImpl load(MyCtNode o) {
							return new ReferenceAnalyzerImpl(o);
						}
					}
				);
		varDeclCache = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<CtVariableReference<?>, Unit<CtVariable<?>>>() {
						public Unit<CtVariable<?>> load(CtVariableReference<?> vref) {
							return new Unit<CtVariable<?>>(vref.getDeclaration());
						}
					}
				);
		typeDeclCache = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<CtTypeReference<?>, Unit<CtType<?>>>() {
						public Unit<CtType<?>> load(CtTypeReference<?> tref) {
							return new Unit<CtType<?>>(tref.getDeclaration());
						}
					}
				);
		exceptionCache = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<CtPackage, Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>>>() {
						public Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>> load(CtPackage root) {
							Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>> ret = new HashMap<CtTypeReference<Object>, List<CtExecutableReference<Object>>>();
							getAllExceptionConstructor(root, ret);
							return ret;
						}
					}
				);
		
		isInterfaceCache = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<CtTypeReference<?>, Boolean>() {
						@Override
						public Boolean load(CtTypeReference<?> t) throws Exception {
							boolean ret = false;
							try {
								ret = t.isInterface();
							}
							catch (Exception e) {
							}
							return ret;
						}
					}
				);
	}

	// This can be called to force the claim of some cache space
	
	static class ReferenceAnalyzerImpl {
		// We are going to use Strings here because the query will come from different
		// meta-model. I know this will miss something due to the scope or maybe not,
		// because of the special rule in java
		HashSet<String> refSigs;

		HashSet<String> typeRefSigs;
		HashSet<String> targetedTypeRefSigs;

		HashSet<CtExecutableReference<Object>> methods;
		HashSet<CtExecutableReference<Object>> constructors;
		HashSet<CtFieldReference<Object>> fields;
		HashSet<CtVariableReference<Object>> variables;

		HashSet<CtTypeReference<?>> targetedTypes;
		HashSet<CtTypeReference<?>> types;

		class ReferenceAnaBuilder extends CtScanner {

			ReferenceAnalyzerImpl p;

			ReferenceAnaBuilder() {
				p = ReferenceAnalyzerImpl.this;
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public void enter(CtElement ele) {
				if (ele instanceof CtClass) {
					CtTypeReference<?> classtref = ele.getFactory().Type().createReference("java.lang.Class");
					CtFieldReference<Object> classfref = (CtFieldReference<Object>) ele.getFactory().Field().createReference(((CtClass) ele).getReference(), classtref, "class");
					p.refSigs.add(classfref.getQualifiedName());
					fields.add(classfref);
				}
				if (ele instanceof CtLocalVariable) {
					CtLocalVariable<Object> loc = (CtLocalVariable<Object>) ele;
					CtVariableReference<Object> ref = loc.getReference();
					p.refSigs.add(ref.toString());
					variables.add(ref);
				}
				if (ele instanceof CtParameter) {
					CtParameter<Object> para = (CtParameter<Object>) ele;
					// XXX: right now we are not going to process lambda expr
					if (para.getParent() instanceof CtLambda)
						return;
					CtVariableReference<Object> ref = para.getReference();
					// XXX: This is anonymous parameters we do not support
					if (ref.toString().contains("$"))
						return;
					p.refSigs.add(ref.toString());
					variables.add(ref);
				}
				if (ele instanceof CtMethod) {
					CtMethod<Object> m = (CtMethod<Object>) ele;
					CtExecutableReference<Object> ref = m.getReference();
					p.refSigs.add(ref.toString());
					methods.add(ref);
				}
				if (ele instanceof CtConstructor) {
					CtConstructor<Object> c = (CtConstructor<Object>) ele;
					CtExecutableReference<Object> ref = c.getReference();
					p.refSigs.add(ref.toString());
					constructors.add(ref);
				}
				if (ele instanceof CtField) {
					CtField<Object> fi = (CtField<Object>) ele;
					CtFieldReference<Object> ref = fi.getReference();
					// Not sure why enum will have null type here
					if (ref.getType() == null) {
						CtElement pele = StaticAnalyzer.getDeclaration(ref).getParent();
						assert(pele instanceof CtEnum);
						CtEnum em = (CtEnum) pele;
						ref.setType(em.getReference());
						ref.setFinal(true);
						ref.setStatic(true);
					}
					p.refSigs.add(ref.getQualifiedName());
					fields.add(ref);
				}
				if (ele instanceof CtInvocation || ele instanceof CtFieldAccess) {
					CtTargetedExpression texp = (CtTargetedExpression) ele;
					if (texp.getTarget() != null) {
						CtTypeReference ttype = texp.getTarget().getType();
						if (ttype == null) {
							if (texp.getTarget() instanceof CtCodeSnippetExpression) {
								CtCodeSnippetExpression sexp = (CtCodeSnippetExpression) texp.getTarget();
								ttype = texp.getFactory().Type().createReference(sexp.getValue());
							}
							if (texp.getTarget() instanceof CtFieldAccess) {
								CtFieldAccess acc = (CtFieldAccess) texp.getTarget();
								ttype = acc.getVariable().getType();
							}
						}
						if (!ttype.isPrimitive()) {
							targetedTypes.add(ttype);
							targetedTypeRefSigs.add(normalizeTypeStr(ttype.toString()));
						}
					}
				}
			}

			@SuppressWarnings({ "unchecked" })
			@Override
			public void enterReference(CtReference ref) {
				if (ref instanceof CtTypeReference) {
					CtTypeReference<?> tref = (CtTypeReference<?>) ref;
					if (!tref.isPrimitive()) {
						types.add(tref);
						typeRefSigs.add(normalizeTypeStr(tref.toString()));
					}
				}
				if (!(ref instanceof CtExecutableReference) && !(ref instanceof CtVariableReference))
					return;
				/*CtElement decEle = ref.getDeclaration();
				if (decEle == null)
					return;*/
				if (ref instanceof CtFieldReference)
					p.refSigs.add(((CtFieldReference<?>) ref).getQualifiedName());
				else
					p.refSigs.add(ref.toString());
				if (ref instanceof CtVariableReference) {
					if (ref instanceof CtFieldReference) {
						CtFieldReference<Object> fref = (CtFieldReference<Object>)ref;
						// XXX: This is an ugly hack to set .class field access
						if (ref.toString().endsWith(".class")) {
							Factory f = ref.getFactory();
							fref.setType(f.Type().createReference("java.lang.Class"));
						}
						fields.add(fref);
					}
					else {
						CtVariableReference<Object> vref = (CtVariableReference<Object>)ref;
						// XXX: This is some anonymous parameters we do not handle right now
						if (vref.toString().contains("$"))
							return;
						variables.add(vref);
					}
				}
				else if (ref instanceof CtExecutableReference) {
					CtExecutableReference<Object> eref = (CtExecutableReference<Object>)ref;
					/*
					CtExecutable<Object> exec = ((CtExecutableReference<Object>) ref).getDeclaration();*/
					if (eref.isConstructor())
						constructors.add(eref);
						//constructors.add((CtConstructor<Object>)exec);
					else
						methods.add(eref);
						//methods.add((CtMethod<Object>)exec);
				}
			}
		}

		public ReferenceAnalyzerImpl(MyCtNode tree) {
			this.refSigs = new HashSet<String>();
			this.typeRefSigs = new HashSet<String>();
			this.targetedTypeRefSigs = new HashSet<String>();
			this.methods = new HashSet<CtExecutableReference<Object>>();
			this.constructors = new HashSet<CtExecutableReference<Object>>();
			this.fields = new HashSet<CtFieldReference<Object>>();
			this.variables = new HashSet<CtVariableReference<Object>>();

			this.types = new HashSet<CtTypeReference<?>>();
			this.targetedTypes = new HashSet<CtTypeReference<?>>();

			ReferenceAnaBuilder builder = new ReferenceAnaBuilder();
			tree.acceptScanner(builder);
		}

		public boolean contains(CtReference n) {
			assert(!(n instanceof CtTypeReference));
			// Field needs to get qualified name to know his types
			if (n instanceof CtFieldReference)
				return refSigs.contains(((CtFieldReference<?>) n).getQualifiedName());
			else
				return refSigs.contains(n.toString());
		}

		private String normalizeTypeStr(String str) {
			StringBuffer ret = new StringBuffer();
			int cnt = 0;
			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);
				if (c == '<') cnt ++;
				if (cnt == 0) ret.append(c);
				if (c == '>') cnt --;
			}
			return ret.toString();
		}
		
		public boolean containsType(CtTypeReference<?> n) {
			return typeRefSigs.contains(normalizeTypeStr(n.toString()));
		}

		public boolean containsTargetedType(CtTypeReference<?> n) {
			return targetedTypeRefSigs.contains(normalizeTypeStr(n.toString()));
		}
	}

	public StaticAnalyzer(MyCtNode tree) {
		this.tree = tree;
		MyCtNode curNode = tree.parentEleNode();
		this.scopeHelper = Collections.newSetFromMap(new IdentityHashMap<CtElement, Boolean>());
		this.enclosingLabels = new HashSet<String>();
		while (curNode != null) {
			if (curNode.isEleClass(CtLoop.class) || curNode.isEleClass(CtBlock.class) || 
					curNode.isEleClass(CtMethod.class) || curNode.isEleClass(CtConstructor.class) || 
					curNode.isEleClass(CtCodeElement.class)) {
				this.scopeHelper.add((CtElement)curNode.getRawObject());
				if (curNode.getRawObject() instanceof CtStatement) {
					enclosingLabels.add(((CtStatement) curNode.getRawObject()).getLabel());
				}
			}
			if (curNode.isEleClass(CtMethod.class) || curNode.isEleClass(CtConstructor.class) || curNode.isEleClass(CtPackage.class)) break;
			curNode = curNode.parentEleNode();
		}
		if (curNode.isEleClass(CtPackage.class))
			this.parentFunc = null;
		else
			this.parentFunc = curNode;
		while (curNode != null) {
			if (curNode.isEleClass(CtLoop.class) || curNode.isEleClass(CtBlock.class) || 
					curNode.isEleClass(CtMethod.class) || curNode.isEleClass(CtConstructor.class) || 
					curNode.isEleClass(CtCodeElement.class)) {
				this.scopeHelper.add((CtElement)curNode.getRawObject());
				if (curNode.getRawObject() instanceof CtStatement) {
					enclosingLabels.add(((CtStatement) curNode.getRawObject()).getLabel());
				}
			}
			if (curNode.isEleClass(CtPackage.class)) break;
			curNode = curNode.parentEleNode();
		}
		
		CtScanner scopeDiscover = new CtScanner() {
			@Override
			public void enter(CtElement ele) {
				if ((ele instanceof CtLoop) || (ele instanceof CtBlock) || 
						(ele instanceof CtMethod) || (ele instanceof CtConstructor) || (ele instanceof CtCodeElement)) {
					StaticAnalyzer.this.scopeHelper.add(ele);
				}
			}
		};
		tree.acceptScanner(scopeDiscover);
		
		this.parentPackage = curNode;
		treeImpl = impls.getUnchecked(tree);
		if (parentFunc != null)
			parentFuncImpl = impls.getUnchecked(parentFunc);
		else
			parentFuncImpl = treeImpl;
		if (parentPackage != null)
			parentPackImpl = impls.getUnchecked(parentPackage);
		else
			parentPackImpl = parentFuncImpl;
	}

	public boolean inBefore(CtReference n) {
		return treeImpl.contains(n);
	}

	public boolean inFunc(CtReference n) {
		return parentFuncImpl.contains(n);
	}

	public boolean inFile(CtReference n) {
		return parentPackImpl.contains(n);
	}

	public static CtTypeBinding findTypeBinding(Factory f, String qualifiedName, boolean innertype) {
		int innerTypeIndex = qualifiedName.lastIndexOf(CtType.INNERTTYPE_SEPARATOR);
		if (innerTypeIndex > 0) {
			if (!innertype) return null;
			String s = qualifiedName.substring(0, innerTypeIndex);
			String simpName = qualifiedName.substring(innerTypeIndex + 1);
			CtTypeBinding t = findTypeBinding(f, s, innertype);
			if (t == null)
				return null;
			for (CtTypeBinding tb : t.getInnerTypes()) {
				if (tb.getSimpleName().equals(simpName)) {
					return tb;
				}
			}
			return null;
		}

		int packageIndex = qualifiedName.lastIndexOf(CtPackage.PACKAGE_SEPARATOR);
		CtPackage pack;
		if (packageIndex > 0) {
			pack = f.Package().get(qualifiedName.substring(0, packageIndex));
		} else {
			pack = f.Package().get(CtPackage.TOP_LEVEL_PACKAGE_NAME);
		}

		if (pack == null) {
			return null;
		}

		return pack.getTypeBinding(qualifiedName.substring(packageIndex + 1));
	}
	
	public static CtTypeBinding findTypeBinding(Factory f, CtTypeReference<?> tref, boolean innertype) {
		String qualifiedName = tref.getQualifiedName();
		return findTypeBinding(f, qualifiedName, innertype);
	}

	public boolean inBinding(CtReference n) {
		if (inFile(n)) return true;
		CtTypeReference<?> dect = null;
		if (n instanceof CtFieldReference)
			dect = ((CtFieldReference<?>) n).getDeclaringType();
		else if (n instanceof CtExecutableReference)
			dect = ((CtExecutableReference<?>) n).getDeclaringType();

		if (dect == null) return false;

		Factory f = n.getFactory();
		CtTypeBinding tbind = findTypeBinding(f, dect, false);
		if (tbind == null) return false;
		if (n instanceof CtFieldReference) {
			// class field is always there
			if (n.getSimpleName().equals("class"))
				return true;
			for (CtFieldBinding fbind : tbind.getFields()) {
				if (fbind.getSimpleName().equals(n.getSimpleName()))
					return true;
			}
		}
		else if (n instanceof CtExecutableReference) {
			for (CtMethodBinding mbind : tbind.getMethods()) {
				// XXX: For now we do not handle the static binding calls
				//if (mbind.isStatic()) continue;
				// I want to get rid of methods that contains
				// unsupported type parameters.
				try {
					mbind.getReference();
				}
				catch (RuntimeException e) {
					// XXX: This contains some hacky type parameters which I do not want to handle for now
					continue;
				}
				if (mbind.getSimpleName().equals(n.getSimpleName()))
					return true;
			}
		}
		return false;
	}

	public ArrayList<CtExecutableReference<Object>> refConstructors(Factory f, HashSet<CtExecutableReference<Object>> constructors) {
		return new ArrayList<CtExecutableReference<Object>>(constructors);
		/*ArrayList<CtExecutableReference<Object>> ret = new ArrayList<CtExecutableReference<Object>>();
		for (CtConstructor<Object> c: constructors) {
			assert( f == c.getFactory());
			ret.add(c.getReference());
		}
		return ret;*/
	}

	@SuppressWarnings("unchecked")
	public boolean checkValid(Factory f, CtVariableReference<Object> vref) {
		CtVariable<Object> v = (CtVariable<Object>) getDeclaration(vref);
		// XXX: This may happen for the lambda expression which we do not handle for now 
		if (v == null && (vref instanceof CtParameterReference))
			return false;
		// Something weird, we should be able to see a local variable at least
		assert( v != null);
		if ((v instanceof CtLocalVariable) || (v instanceof CtParameter) || (v instanceof CtCatchVariableImpl)) {
			if (!scopeHelper.contains(v.getParent())) return false;
			if (v instanceof CtLocalVariable) {
				MyCtNode lastBlock = tree;
				MyCtNode parentBlock = tree.parentEleNode();
				while (parentBlock != null && parentBlock.getRawObject() != v.getParent()) {
					lastBlock = parentBlock;
					parentBlock = parentBlock.parentEleNode();
				}
				// XXX: If parentBlock is null, it means this declaration is inside the tree
				// I do not impose check in this case. I may miss some of invalid trees but
				// should not be that many to care
				if (parentBlock != null) { 
					// The local variable needs to appear first
					if (v.getParent() instanceof CtStatementList) {
						CtStatementList l = (CtStatementList) v.getParent();
						boolean bad = true;
						// XXX: This may cause to accept some invalid snippet
						// where it uses the declared local variables in tree in a
						// inserted statement at the start, but it only for just
						// one variable for most cases. I am happy to live with 
						// it right now.
						if (tree.isCollection()) {
							int m = tree.getNumChildren();
							for (int i = 0; i < m; i++) {
								if (tree.getChild(i).getRawObject() == v ) {
									bad = false;
									break;
								}
							}
						}
						else if (tree.getRawObject() == v)
							bad = false;
						if (bad) {
							for (CtStatement stmt : l.getStatements()) {
								if (stmt == v) {
									bad = false;
									break;
								}
								if (lastBlock.isCollection()) {
									// XXX: This is strange, but I will just let you pass
									if (lastBlock.getNumChildren() == 0) {
										bad = false;
										break;
									}
									if (lastBlock.getChild(0).getRawObject() == stmt)
										break;
								}
								else if (lastBlock.getRawObject() == stmt)
									break;
							}
						}
						if (bad) return false;
					}
				}
			}
		}
		assert( f == v.getFactory());
		return true;
	}
	
	public ArrayList<CtVariableReference<Object>> refVariables(Factory f, HashSet<CtVariableReference<Object>> variables) {
		ArrayList<CtVariableReference<Object>> ret = new ArrayList<CtVariableReference<Object>>();
		for (CtVariableReference<Object> vref : variables) {
			if (!checkValid(f, vref)) {
				continue;
			}
			ret.add(vref);
		}
		return ret;
	}

	public ArrayList<CtFieldReference<Object>> refFields(Factory f, HashSet<CtFieldReference<Object>> fields) {
		return new ArrayList<CtFieldReference<Object>>(fields);
		/*ArrayList<CtFieldReference<Object>> ret = new ArrayList<CtFieldReference<Object>>();
		for (CtFieldReference<Object> fref : fields) {
			assert( f == fref.getFactory());
			//assert( f == fi.getFactory());
			//CtFieldReference<Object> fref = fi.getReference();
			// XXX: Not sure why enum will get a null type field, probably a bug
			// in spoon
			if (fref.getType() == null) {
				CtElement pele = fref.getDeclaration().getParent();
				assert(pele instanceof CtEnum);
				CtEnum em = (CtEnum) pele;
				fref.setType(em.getReference());
			}
			ret.add(fref);
		}
		return ret;*/
	}

	public ArrayList<CtExecutableReference<Object>> refMethods(Factory f, HashSet<CtExecutableReference<Object>> methods) {
		return new ArrayList<CtExecutableReference<Object>>(methods);
		/*ArrayList<CtExecutableReference<Object>> ret = new ArrayList<CtExecutableReference<Object>>();
		for (CtMethod<Object> m : methods) {
			assert( f == m.getFactory());
			ret.add(m.getReference());
		}
		return ret;*/
	}

	public ArrayList<CtExecutableReference<Object>> getMethodInFile(Factory f) {
		if (parentPackage == null) return null;
		return refMethods(f, parentPackImpl.methods);
	}

	public ArrayList<CtExecutableReference<Object>> getMethodInFunc(Factory f) {
		return refMethods(f, parentFuncImpl.methods);
	}

	public ArrayList<CtExecutableReference<Object>> getMethodInBefore(Factory f) {
		return refMethods(f, treeImpl.methods);
	}

	public ArrayList<CtFieldReference<Object>> getFieldInFile(Factory f) {
		if (parentPackage == null) return null;
		return refFields(f, parentPackImpl.fields);
	}

	public ArrayList<CtFieldReference<Object>> getFieldInFunc(Factory f) {
		return refFields(f, parentFuncImpl.fields);
	}

	public ArrayList<CtFieldReference<Object>> getFieldInBefore(Factory f) {
		if (tree == null) return null;
		return refFields(f, treeImpl.fields);
	}

	public ArrayList<CtVariableReference<Object>> getVarInFile(Factory f) {
		if (parentPackage == null) return null;
		return refVariables(f, parentPackImpl.variables);
	}

	public ArrayList<CtVariableReference<Object>> getVarInFunc(Factory f) {
		if (parentFunc == null) return refVariables(f, treeImpl.variables);
		return refVariables(f, parentFuncImpl.variables);
	}

	public ArrayList<CtVariableReference<Object>> getVarInBefore(Factory f) {
		if (tree == null) return null;
		return refVariables(f, treeImpl.variables);
	}

	public ArrayList<CtExecutableReference<Object>> getConstructorInFile(Factory f) {
		if (parentPackage == null) return null;
		return refConstructors(f, parentPackImpl.constructors);
	}

	public ArrayList<CtExecutableReference<Object>> getConstructorInFunc(Factory f) {
		return refConstructors(f, parentFuncImpl.constructors);
	}

	public ArrayList<CtExecutableReference<Object>> getConstructorInBefore(Factory f) {
		if (tree == null) return null;
		return refConstructors(f, treeImpl.constructors);
	}

	public CtTypeReference<?> getThisTypeReference() {
		if (parentFunc == null)
			return null;
		if (parentFunc.getRawObject() instanceof CtMethod) {
			CtMethod<?> m = (CtMethod<?>)parentFunc.getRawObject();
			return m.getDeclaringType().getReference();
		}
		else if (parentFunc.getRawObject() instanceof CtConstructor) {
			CtConstructor<?> m = (CtConstructor<?>)parentFunc.getRawObject();
			return m.getDeclaringType().getReference();
		}
		else
			return null;
	}

	public ArrayList<CtVariableReference<?>> getVarRefsInFile(Factory f) {
		ArrayList<CtVariableReference<?>> ret = new ArrayList<CtVariableReference<?>>();
		ret.addAll(getVarInFile(f));
		ret.addAll(getFieldInFile(f));
		return ret;
	}

	public ArrayList<CtVariableReference<?>> getVarRefsInFunc(Factory f) {
		ArrayList<CtVariableReference<?>> ret = new ArrayList<CtVariableReference<?>>();
		ret.addAll(getVarInFunc(f));
		ret.addAll(getFieldInFunc(f));
		return ret;
	}

	public ArrayList<CtVariableReference<?>> getVarRefsInBefore(Factory f) {
		ArrayList<CtVariableReference<?>> ret = new ArrayList<CtVariableReference<?>>();
		ret.addAll(getVarInBefore(f));
		ret.addAll(getFieldInBefore(f));
		return ret;
	}

	public int getNumVarRefsInFile() {
		if (parentPackage == null) return 0;
		return parentPackImpl.fields.size() + numVariables(parentPackImpl.variables);
	}

	private int numVariables(HashSet<CtVariableReference<Object>> variables) {
		int ret = 0;
		for (CtVariableReference<Object> vref : variables) {
			CtVariable<Object> v = vref.getDeclaration();
			if ((v instanceof CtLocalVariable) || (v instanceof CtParameter)) {
				if (!scopeHelper.contains(v.getParent())) continue;
			}
			ret ++;
		}
		return ret;
	}

	public int getNumExecRefsInFile() {
		if (parentPackage == null) return 0;
		return parentPackImpl.constructors.size() + parentPackImpl.methods.size();
	}

	public int getNumVarRefsInFunc() {
		return parentFuncImpl.fields.size() + numVariables(parentFuncImpl.variables);
	}

	public int getNumExecRefsInFunc() {
		return parentFuncImpl.constructors.size() + parentFuncImpl.methods.size();
	}

	public int getNumVarRefsInBefore() {
		if (tree == null) return 0;
		return treeImpl.fields.size() + numVariables(treeImpl.variables);
	}

	public int getNumExecRefsInBefore() {
		if (tree == null) return 0;
		return treeImpl.constructors.size() + treeImpl.methods.size();
	}

	public boolean inBeforeBindType(CtTypeReference<?> n) {
		return treeImpl.containsTargetedType(n);
	}

	public boolean inFuncBindType(CtTypeReference<?> n) {
		return treeImpl.containsTargetedType(n) || parentFuncImpl.containsType(n);
	}

	public boolean inFileBindType(CtTypeReference<?> n) {
		return parentFuncImpl.containsTargetedType(n) || parentPackImpl.containsType(n);
	}

	public boolean inBindingBindType(CtTypeReference<?> n) {
		return parentPackImpl.containsTargetedType(n);
	}

	private boolean checkVisibility(CtMethodBinding b) {
		if (b.isPrivate()) return false;
		if (b.isPublic()) return true;
		return b.getDeclaringType().getPackage().toString().equals(parentPackage.toString());
	}
	
	private boolean checkVisibility(CtFieldBinding b) {
		if (b.isPrivate()) return false;
		if (b.isPublic()) return true;
		return b.getDeclaringType().getPackage().toString().equals(parentPackage.toString());
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<CtExecutableReference<Object>> getMethodInBinding(
			ArrayList<CtTypeReference<?>> tlist) {
		ArrayList<CtExecutableReference<Object>> ret = new ArrayList<CtExecutableReference<Object>>();

		for (CtTypeReference<?> tref : tlist) {
			Factory f = tref.getFactory();
			if (f == null)
				throw new GenesisException("Unable to get factory in getMethodInBinding()");
			CtTypeBinding tbind = findTypeBinding(f, tref, false);
			if (tbind == null)
				continue;
			for (CtMethodBinding mbind : tbind.getMethods()) {
				CtExecutableReference<Object> ref = null;
				if (!checkVisibility(mbind))
					continue;
				try {
					ref = (CtExecutableReference<Object>) mbind.getReference();
				}
				catch (RuntimeException e) {
					// XXX: This contains some hacky type parameter which I do not want to handle for now
					continue;
				}
				if (!inFile(ref))
					ret.add(ref);
			}
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private static void getAllExceptionConstructor(CtPackage root, Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>> m) {
		for (CtPackage p : root.getPackages()) {
			getAllExceptionConstructor(p, m);
		}
		for (CtTypeBinding tb : root.getTypeBindings()) {
			CtTypeReference<?> texc = null;
			try {
				texc = tb.getReference();
			}
			catch (RuntimeException e) {
				// XXX: just discard it, this does not work out
				continue;
			}
			
			for (CtMethodBinding mb : tb.getMethods()) {
				if (mb.isConstructor()) {
					try {
						CtExecutableReference<Object> eref = (CtExecutableReference<Object>) mb.getReference();
						if (!m.containsKey(texc))
							m.put((CtTypeReference<Object>) texc, new ArrayList<CtExecutableReference<Object>>());
						m.get((CtTypeReference<Object>) texc).add(eref);
					}
					catch (RuntimeException e) {
						// just ignore
					}
				}
			}
		}
	}
	
	public ArrayList<CtExecutableReference<Object>> getExceptionConstructorInBinding() {
		Factory f = tree.getFactory();
		CtPackage root = f.Package().getRootPackage();
		Map<CtTypeReference<Object>, List<CtExecutableReference<Object>>> m = exceptionCache.getUnchecked(root);
		ArrayList<CtExecutableReference<Object>> l = new ArrayList<CtExecutableReference<Object>>();
		for (Entry<CtTypeReference<Object>, List<CtExecutableReference<Object>>> e: m.entrySet()) {
			CtTypeReference<?> runtimeExc = root.getFactory().Type().createReference("java.lang.RuntimeException");
			if (!TypeHelper.typeCompatible(runtimeExc, e.getKey())) {
				boolean isThrown = false;
				CtExecutable<?> enclosingExec = getEnclosingFunc();
				if (enclosingExec == null)
					continue;
				for (CtTypeReference<?> thrownT : enclosingExec.getThrownTypes()) {
					if (TypeHelper.typeCompatible(thrownT, e.getKey())) {
						isThrown = true;
						break;
					}
				}
				if (!isThrown)
					continue;
			}
			for (CtExecutableReference<Object> v : e.getValue())
				l.add(v);
		}
		return l;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<CtFieldReference<Object>> getFieldInBinding(ArrayList<CtTypeReference<?>> tlist) {
		ArrayList<CtFieldReference<Object>> ret = new ArrayList<CtFieldReference<Object>>();

		for (CtTypeReference<?> tref : tlist) {
			Factory f = tref.getFactory();
			if (f == null)
				throw new GenesisException("Unable to get factory in getFieldInBinding()");
			CtTypeBinding tbind = findTypeBinding(f, tref, false);
			if (tbind == null)
				continue;
			
			// This is to add .class special field in it
			CtTypeReference<?> classtref = f.Type().createReference("java.lang.Class");
			CtFieldReference<Object> classfref = (CtFieldReference<Object>) f.Field().createReference(tref, classtref, "class");
			if (!inFile(classfref))
				ret.add(classfref);
			
			for (CtFieldBinding fbind : tbind.getFields()) {
				if (!checkVisibility(fbind)) continue;
				CtFieldReference<Object> ref = null;
				try {
					ref = (CtFieldReference<Object>) fbind.getReference();
				}
				catch (RuntimeException e) {
					// XXX: It seems the type argument may cause this to fail... but whatever
					continue;
				}
				if (!inFile(ref))
					ret.add(ref);
			}
		}
		return ret;
	}

	public ArrayList<CtTypeReference<?>> getBindTypeInBefore() {
		return new ArrayList<CtTypeReference<?>>(treeImpl.targetedTypes);
	}

	public ArrayList<CtTypeReference<?>> getBindTypeInFunc() {
		ArrayList<CtTypeReference<?>> ret = new ArrayList<CtTypeReference<?>>(treeImpl.types);
		for (CtTypeReference<?> ref : parentFuncImpl.targetedTypes) {
			if (!treeImpl.containsType(ref))
				ret.add(ref);
		}
		return ret;
	}

	public ArrayList<CtTypeReference<?>> getBindTypeInFile() {
		ArrayList<CtTypeReference<?>> ret = new ArrayList<CtTypeReference<?>>(parentFuncImpl.types);
		for (CtTypeReference<?> ref : parentPackImpl.targetedTypes) {
			if (!parentFuncImpl.containsType(ref))
				ret.add(ref);
		}
		return ret;
	}

	public ArrayList<CtTypeReference<?>> getBindTypeInBinding() {
		return new ArrayList<CtTypeReference<?>>(parentPackImpl.types);
	}
	
	public ArrayList<CtTypeReference<?>> getTypesInBefore() {
		return new ArrayList<CtTypeReference<?>>(treeImpl.types);
	}
	
	public ArrayList<CtTypeReference<?>> getTypesInFunc() {
		return new ArrayList<CtTypeReference<?>>(parentFuncImpl.types);
	}
	
	public ArrayList<CtTypeReference<?>> getTypesInFile() {
		return new ArrayList<CtTypeReference<?>>(parentPackImpl.types);
	}
	
	public boolean inBeforeType(CtTypeReference<?> t) {
		return treeImpl.containsType(t);
	}
	
	public boolean inFuncType(CtTypeReference<?> t) {
		return parentFuncImpl.containsType(t);
	}
	
	public boolean inFileType(CtTypeReference<?> t) {
		return parentPackImpl.containsType(t);
	}
	
	public int getNumTypesInBefore() {
		return treeImpl.types.size();
	}
	
	public int getNumTypesInFunc() {
		return parentFuncImpl.types.size();
	}
	
	public int getNumTypesInFile() {
		return parentPackImpl.types.size();
	}
	

	public static CtVariable<?> getDeclaration(CtVariableReference<?> vref) {
		return varDeclCache.getUnchecked(vref).x;
	}

	public static CtType<?> getDeclaration(CtTypeReference<?> tref) {
		return typeDeclCache.getUnchecked(tref).x;
	}

	public int getNumVarRefsInBinding(ArrayList<CtTypeReference<?>> trefs) {
		return getFieldInBinding(trefs).size();
	}

	public int getNumExecRefsInBinding(ArrayList<CtTypeReference<?>> trefs) {
		return getMethodInBinding(trefs).size();
	}

	public CtTypeReference<?> getEnclosingFuncReturnType() {
		if (parentFunc == null || !parentFunc.isEleClass(CtExecutable.class)) return null;
		CtExecutable<?> func = (CtExecutable<?>) parentFunc.getRawObject();
		return func.getType();
	}

	public CtExecutableReference<?> getEnclosingFuncReference() {
		if (parentFunc == null || !parentFunc.isEleClass(CtExecutable.class)) return null;
		CtExecutable<?> func = (CtExecutable<?>) parentFunc.getRawObject();
		return func.getReference();
	}

	public CtExecutable<?> getEnclosingFunc() {
		if (parentFunc == null || !parentFunc.isEleClass(CtExecutable.class)) return null;
		return (CtExecutable<?>) parentFunc.getRawObject();
	}

	public boolean isExceptionType(CtTypeReference<?> texc) {
		CtTypeReference<?> runtimeExc = tree.getFactory().Type().createReference("java.lang.RuntimeException");
		if (!TypeHelper.typeCompatible(runtimeExc, texc)) {
			boolean isThrown = false;
			CtExecutable<?> enclosingExec = getEnclosingFunc();
			if (enclosingExec == null)
				return false;
			for (CtTypeReference<?> thrownT : enclosingExec.getThrownTypes()) {
				if (TypeHelper.typeCompatible(thrownT, texc)) {
					isThrown = true;
					break;
				}
			}
			if (!isThrown)
				return false;
		}
		return true;
	}

	public Set<String> getEnclosingLabels() {
		return enclosingLabels;
	}

	public boolean isFieldNameExist(String simpleName) {
		for (CtFieldReference<?> fref : treeImpl.fields) {
			if (fref.getSimpleName().equals(simpleName))
				return true;
		}
		return false;
	}

	public boolean isExecNameExist(String simpleName) {
		for (CtExecutableReference<?> eref : treeImpl.methods)
			if (eref.getSimpleName().equals(simpleName))
				return true;
		return false;
	}

	public boolean inBeforeVarName(String vname) {
		for (CtVariableReference<?> vref : treeImpl.variables) {
			if (vref.getSimpleName().equals(vname))
				return true;
		}
		return false;
	}

	public static boolean isInterface(CtTypeReference<?> t) {
		return isInterfaceCache.getUnchecked(t);
	}

	// This function tests whether this reference is a result of targeted
	// expression like x.f, and x is not this.
	/*public boolean isComplicatedReference(CtVariableReference<?> var) {
		if (var instanceof CtFieldAccess) {
			CtFieldAccess<?> acc = (CtFieldAccess<?>) var;
			if (acc.getTarget() != null)
				if (!(acc.getTarget() instanceof CtThisAccess))
					return false;
			return true;
		}
		return false;
	}*/
}
