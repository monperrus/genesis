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
package genesis.schema;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.cache.LoadingCache;

import genesis.Config;
import genesis.GenesisException;
import genesis.analysis.TypeHelper;
import genesis.generator.ReferenceGenerator;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.node.MyNodeTrait;
import genesis.transform.InvalidRuleException;
import genesis.transform.ManualTransformRule;
import spoon.Launcher;
import spoon.compiler.SpoonCompiler;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.reflect.code.CtBinaryOperatorImpl;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtCaseImpl;
import spoon.support.reflect.code.CtCodeElementImpl;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtExpressionImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtThrowImpl;
import spoon.support.reflect.code.CtUnaryOperatorImpl;
import spoon.support.reflect.declaration.CtElementImpl;
import spoon.support.util.RtHelper;

public class TransformSchema implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	boolean dup;
	Map<Integer, Set<MyNodeSig>> varContains;
	Set<MyNodeSig> inside;
	TransASTNode pre;
	TransASTNode post;

	transient HashMap<Integer, MyNodeSig> varSigs;
	transient HashSet<Integer> inferableVids;

	TransformSchema() {
		dup = false;
		varContains = null;
		inside = null;
		pre = null;
		post = null;
		varSigs = new HashMap<Integer, MyNodeSig>();
		inferableVids = null;
	}
	
	public TransformSchema(boolean dup, Map<Integer, Set<MyNodeSig>> varContains, Set<MyNodeSig> inside) {
		this();
		this.dup = dup;
		this.varContains = new HashMap<Integer, Set<MyNodeSig>>();
		this.inside = new HashSet<MyNodeSig>(inside);
	}
	
	public void setPre(TransASTNode pre) {
		this.pre = pre;
	}
	
	public void setPost(TransASTNode post) {
		this.post = post;
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
		syncTransient();
	}

	class VarSigCollector extends TransASTVisitor {
		Map<Integer, MyNodeSig> varSigs;

		VarSigCollector(Map<Integer, MyNodeSig> varSigs) {
			this.varSigs = varSigs;
		}

		@Override
		public boolean visitFreeVar(TransASTFreeVar n) {
			varSigs.put(n.vid, n.nodeSig);
			return true;
		}
	}

	private void syncTransient() {
		varSigs = new HashMap<Integer, MyNodeSig>();
		VarSigCollector collector = new VarSigCollector(varSigs);
		collector.scanNode(pre);
		collector.scanNode(post);
	}

	class VarCollector extends TransASTVisitor {
		HashSet<Integer> vids;

		VarCollector() {
			this.vids = new HashSet<Integer>();
		}

		@Override
		public boolean visitFreeVar(TransASTFreeVar n) {
			vids.add(n.vid);
			return true;
		}

		public HashSet<Integer> getResult() {
			return vids;
		}
	}

	HashSet<Integer> getVarsIn(TransASTNode root) {
		VarCollector col = new VarCollector();
		col.scanNode(root);
		return col.getResult();
	}

	public HashSet<Integer> varsInPre() {
		return getVarsIn(pre);
	}

	public HashSet<Integer> varsInPost() {
		HashSet<Integer> ret = getVarsIn(post);
		ret.removeAll(varsInPre());
		return ret;
	}

	public TransASTFreeVar createFreeVar(Integer vid) {
		return new TransASTFreeVar(varSigs.get(vid), vid);
	}

	public TransASTCtEle createCtEle(MyNodeSig sig, Map<String, TransASTNode> children) {
		return new TransASTCtEle(sig, children);
	}

	public TransASTFreeVar createFreshFreeVar(MyNodeSig sig) {
		Integer vid = varSigs.size();
		varSigs.put(vid, sig);
		return new TransASTFreeVar(sig, vid);
	}

	public TransASTCollection createCollection(MyNodeSig sig, List<TransASTNode> children) {
		return new TransASTCollection(sig, children);
	}

	public TransASTNode createTrait(MyNodeSig sig, MyNodeTrait trait) {
		return new TransASTTrait(sig, trait);
	}

	public boolean schemaEquals(TransformSchema s) {
		if (s.dup != dup) return false;
		if (!s.inside.equals(inside)) return false;
		if (!s.varContains.equals(varContains)) return false;
		if (!pre.treeEquals(s.pre)) return false;
		return post.treeEquals(s.post);
	}

	@Override
	public String toString() {
		String res = "dup: " + Boolean.toString(dup) + "\n";
		res += "inside: " + inside.toString() + "\n";
		res += "pre: " + pre.toString() + "\n";
		res += "post: " + post.toString() + "\n";
		res += "varContains: " + varContains.toString() + "\n";
		return res;
	}

	private void computeInferableVids() {
		inferableVids = new HashSet<Integer>();
		final HashSet<Integer> uninferableVids = new HashSet<Integer>();
		TransASTVisitor v = new TransASTVisitor() {
			@Override
			public boolean visitFreeVar(TransASTFreeVar n) {
				if (visitStack.size() > 1) {
					TransASTNode parent = visitStack.get(visitStack.size() - 2);
					if (parent instanceof TransASTCtEle) {
						TransASTCtEle ele = (TransASTCtEle) parent;
						if (ele.nodeSig.isClass(CtExpression.class, false)) {
							if (!ele.nodeSig.isClass(CtTypeAccess.class, false)) { 
								if (ele.children.get("type") == n)
									inferableVids.add(n.vid);
								if (ele.nodeSig.isClass(CtLiteral.class, false))
									if (ele.children.get("value") == n)
										uninferableVids.add(n.vid);
							}
						}
						else if (ele.nodeSig.isClass(CtVariable.class, false)) {
							if (ele.children.get("type") == n)
								uninferableVids.add(n.vid);
						}
					}
					else if (parent instanceof TransASTCollection && visitStack.size() > 2) {
						TransASTNode grandParent = visitStack.get(visitStack.size() - 3);
						if (grandParent instanceof TransASTCtEle) {
							TransASTCtEle ele = (TransASTCtEle) grandParent;
							if (ele.nodeSig.isClass(CtExpression.class, false)) { 
								if (ele.children.get("typeCasts") == parent)
									inferableVids.add(n.vid);
							}
						}
					}
				}
				return true;
			}
		};
		v.scanNode(post);
		inferableVids.removeAll(uninferableVids);
	}

	public boolean isInferableTypeVid(Integer vid) {
		if (inferableVids == null)
			computeInferableVids();
		return inferableVids.contains(vid);
	}

	static class PostTreeBuilder extends TransASTVisitor {
		Factory spoonf;
		HashMap<Integer, MyCtNode> varBindings;
		HashMap<TransASTNode, MyCtNode> resM;
		LoadingCache<MyCtNode, MyCtNode> cloneCache;
		boolean malformed;

		PostTreeBuilder(Factory spoonf, HashMap<Integer, MyCtNode> varBindings) {
			this.spoonf = spoonf;
			this.varBindings = varBindings;
			this.resM = new HashMap<TransASTNode, MyCtNode>();
			this.malformed = false;
			this.cloneCache = null;
		}
		
		PostTreeBuilder(Factory spoonf, HashMap<Integer, MyCtNode> varBindings, LoadingCache<MyCtNode, MyCtNode> cloneCache) {
			this(spoonf, varBindings);
			this.cloneCache = cloneCache;
		}

		@Override
		public boolean visitFreeVar(TransASTFreeVar n) {
			if (!varBindings.containsKey(n.vid)) {
				throw new GenesisException("Cannot build post-tree with missing var binding " + n.vid);
			}
			MyCtNode r = null;
			MyCtNode bindr = varBindings.get(n.vid);
			// We disable clone cache for Expressions because 
			// They may be modified after the 
			if (cloneCache == null || bindr.isColClass(CtExpressionImpl.class) || bindr.isEleClass(CtExpressionImpl.class))
				r = bindr.deepClone();
			else 
				r = cloneCache.getUnchecked(bindr);
			resM.put(n, r);
			return true;
		}

		@Override
		public boolean visitTrait(TransASTTrait n) {
			resM.put(n, n.trait.convertToNodeWithClone(spoonf));
			return true;
		}

		@Override
		public boolean scanCollection(TransASTCollection n) {
			boolean ret = super.scanCollection(n);
			if (malformed) return false;
			ArrayList<Object> r = new ArrayList<Object>();
			for (int i = 0; i < n.children.size(); i++) {
				MyCtNode node = resM.get(n.children.get(i));
				if (malformed) return false;
				r.add(node.getRawObject());
			}
			resM.put(n, new MyCtNode(r, false));
			return ret;
		}

		@SuppressWarnings("unchecked")
		private boolean checkBlockStatements(Object v) {
			if (!(v instanceof List)) {
				return false;
			}
			for (Object o : (List<Object>)v) {
				if (!(o instanceof CtStatement)) {
					return false;
				}
			}
			return true;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public boolean scanCtEle(TransASTCtEle n) {
			boolean ret = super.scanCtEle(n);
			if (malformed) return false;
			Class<?> clazz = n.nodeSig.getClassSig();
			CtElement r = null;
			try {
				r = (CtElement)clazz.newInstance();
				for (Field f : RtHelper.getAllFields(clazz)) {
					String name = f.getName();
					if (!n.children.containsKey(name)) continue;
					f.setAccessible(true);
					MyCtNode node = resM.get(n.children.get(name));
					Object v = node.getRawObject();
					// XXX: CtBlock#statements and CtCaseImpl#statements must have CtStatement
					if (r instanceof CtBlockImpl || r instanceof CtCaseImpl)
						if (name.equals("statements")) {
							if (!checkBlockStatements(v)) {
								malformed = true;
								return false;
							}
						}
					if (r instanceof CtIfImpl)
						if (name.equals("thenStatement") || name.equals("elseStatement"))
							if (v != null && !(v instanceof CtStatement)) {
								malformed = true;
								return false;
							}
					if (r instanceof CtBinaryOperatorImpl)
						if (name.equals("leftHandOperand") || name.equals("rightHandOperand"))
							if (v == null || !(v instanceof CtExpression)) {
								malformed = true;
								return false;
							}
					if (r instanceof CtUnaryOperatorImpl)
						if (name.equals("operand"))
							if (v == null || !(v instanceof CtExpression)) {
								malformed = true;
								return false;
							}
					if (v instanceof Collection<?>) {
						Collection<?> c = (Collection<?>) v;
						for (Object o : c) {
							if (o instanceof CtElementImpl) {
								CtElementImpl i = (CtElementImpl) o;
								i.setParent(r);
							}
						}
					}
					else if (v instanceof CtElementImpl) {
						CtElementImpl i = (CtElementImpl) v;
						i.setParent(r);
					}
					
					// I can only convert to Set here because Set Implementation will use
					// PrettyPrint in spoon, which requires the parent field being set. 
					if (Set.class.isAssignableFrom(f.getType())) { 
						f.set(r, new TreeSet<Object>((Collection<Object>) v));
					}
					else
						f.set(r, v);
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				assert(false);
			}
			r.setFactory(spoonf);
			resM.put(n, new MyCtNode(r, false));
			return ret;
		}

		MyCtNode getResult(TransASTNode n) {
			if (resM.containsKey(n) && !malformed)
				return resM.get(n);
			else
				return null;
		}
	}


	private static class VarTypeVisitor extends TransASTVisitor {

		Factory f;
		Integer id;
		HashMap<Integer, MyCtNode> vb;
		VarTypeContext res;

		VarTypeVisitor(Factory f, Integer id, HashMap<Integer, MyCtNode> vb, HashSet<Integer> banVid) {
			this.f = f;
			this.id = id;
			this.vb = new HashMap<Integer, MyCtNode>(vb);
			for (Integer vid: banVid) {
				this.vb.remove(vid);
			}
			this.res = new VarTypeContext();
		}

		@Override
		public boolean visitFreeVar(TransASTFreeVar n) {
			if (n.vid == id) {
				res.n = n;
				if (visitStack.size() > 1) {
					TransASTNode parent = visitStack.get(visitStack.size() - 2);
					res.parent = parent;
					if (parent instanceof TransASTCtEle) {
						TransASTCtEle ele = (TransASTCtEle) parent;
						if (ele.nodeSig.isClass(CtTargetedExpression.class, false)) {
							TransASTNode targetE = ele.children.get("target");
							MyCtNode buildRes = null;
							try {
								PostTreeBuilder b = new PostTreeBuilder(f, vb);
								b.scanNode(targetE);
								buildRes = b.getResult(targetE);
							}
							catch (GenesisException e) {
								// If this happens, it means we cannot get the typeref
							}
							if (buildRes != null && !buildRes.isTrait()) {
								try {
									TypeHelper.inferAllTypesIn(buildRes);
									res.targetType = TypeHelper.getExprType(buildRes);
								}
								catch (GenesisException e) {
									// If the inference failed, we are going to give up too
								}
							}
						}
						if (ele.nodeSig.isClass(CtConstructorCallImpl.class, false)) {
							TransASTNode n1 = ele.children.get("executable");
							if (n == n1) {
								res.constructorOnly = true;
								if (visitStack.size() > 2) {
									TransASTNode grandParent = visitStack.get(visitStack.size() - 3);
									if (grandParent.nodeSig.isClass(CtThrowImpl.class, false))
										res.exceptionOnly = true;
								}
							}
						}
					}
				}
			}
			return true;
		}
	}

	public VarTypeContext getVarTypeContext(Factory f, Integer vid, HashMap<Integer, MyCtNode> vb) {
		// XXX: Given the current generation structure, we cannot work with any var that in the post only
		HashSet<Integer> banVid = varsInPost();
		VarTypeVisitor v = new VarTypeVisitor(f, vid, vb, banVid);
		v.scanNode(post);
		return v.res;
	}

	public boolean canGenStatementNodeOnly() {
		// XXX: This is to avoid the empty collection get eliminated
		// Because the signature of the FreeVar is calculated as the upper 
		// bound and the empty set has the lowest signature
		if (post instanceof TransASTFreeVar)
			return false;
		if (post instanceof TransASTCollection) {
			TransASTCollection col = (TransASTCollection) post;
			for (TransASTNode node : col.children) {
				if (!(node instanceof TransASTFreeVar))
					if (canGenStatementOnly(node.nodeSig))
						return true;
			}
		}
		else {
			if (canGenStatementOnly(post.nodeSig))
				return true;
		}
		return false;
	}

	private boolean canGenStatementOnly(MyNodeSig nodeSig) {
		Class<?> clazz = nodeSig.getClassSig();
		if (clazz == null) return false;
		return (CtStatement.class.isAssignableFrom(clazz) &&
				!CtExpression.class.isAssignableFrom(clazz));
	}

	public boolean canGenExpressionNodeOnly() {
		// XXX: This is to avoid the empty collection get eliminated
		// Because the signature of the FreeVar is calculated as the upper 
		// bound and the empty set has the lowest signature
		if (post instanceof TransASTFreeVar)
			return false;
		
		if (post instanceof TransASTCollection) {
			TransASTCollection col = (TransASTCollection) post;
			for (TransASTNode node : col.children) {
				if (!(node instanceof TransASTFreeVar))
					if (canGenExpressionOnly(node.nodeSig))
						return true;
			}
		}
		else {
			if (canGenExpressionOnly(post.nodeSig))
				return true;
		}
		return false;
	}

	private boolean canGenExpressionOnly(MyNodeSig nodeSig) {
		Class<?> clazz = nodeSig.getClassSig();
		if (clazz == null) return false;
		return (!CtStatement.class.isAssignableFrom(clazz) &&
				CtExpression.class.isAssignableFrom(clazz));
	}
	
	public boolean canGenClassNode() {
		if (post instanceof TransASTFreeVar)
			return true;
		if (post.nodeSig.getClassSig() == null)
			return false;
		return CtClass.class.isAssignableFrom(post.nodeSig.getClassSig());
	}

	public int getOnlyVarIdxInPost() {
		if (post instanceof TransASTFreeVar) {
			return ((TransASTFreeVar) post).vid;
		}
		else
			return -1;
	}

	/** Note that this call may change the vargens in ManualTransformRule **/
	public static TransformSchema buildFromClass(ManualTransformRule r) {
		TransformSchema ret = new TransformSchema();
		ret.dup = r.dup;
		ret.inside = new HashSet<MyNodeSig>(r.inside);
		ret.varContains = new HashMap<Integer, Set<MyNodeSig>>();
		ret.varSigs = new HashMap<Integer, MyNodeSig>(r.varsigs);
		ret.pre = parseTransAST(ret, r, "preAST");
		ret.post = parseTransAST(ret, r, "postAST");
		return ret;
	}
	
	private static boolean isFuncCall(CtStatement stmt, String fname) {
		if (!(stmt instanceof CtInvocation))
			return false;
		return ((CtInvocation<?>)stmt).getExecutable().toString().contains("#" + fname);
	}
	
	private static TransASTNode parseTransAST(TransformSchema schema, ManualTransformRule r, String m) {
		String className = r.getClass().getName();
		SpoonCompiler comp = new Launcher().createCompiler();
		try {
			comp.addInputSource(SpoonResourceHelper.createResource(new File(Config.transformSourceDir
					+ className.replace('.', '/') + ".java")));
		}
		catch (FileNotFoundException e) {
			throw new InvalidRuleException("Unable to find the source file of the rule: " + className + "\n" + e.getMessage());
		}
		comp.build();
		// Now we get the method tree
		CtMethod<?> method = comp.getFactory().Class().get(className).getMethodsByName(m).get(0);
		CtBlock<?> block = method.getBody();
		
		MyCtNode root = null;
		int idx;
		switch (r.topSig) {
		case ColCodeEleSig:
			ArrayList<CtCodeElementImpl> l = new ArrayList<CtCodeElementImpl>();
			boolean start = false;
			for (CtStatement s : block.getStatements()) {
				if (!start) {
					if (isFuncCall(s, "sugarStart"))
						start = true;
					continue;
				}
				if (isFuncCall(s, "sugarEnd"))
					break;
				// We skip the last statement
				//if (s == block.getLastStatement()) break;
				l.add((CtCodeElementImpl) s);
			}
			root = new MyCtNode(l, false);
			break;
		case CodeEleSig:
			idx = 0;
			while (!isFuncCall(block.getStatement(idx), "sugarStart"))
				idx ++;
			root = new MyCtNode(block.getStatement(idx + 1), false);
			break;
		case ColExprSig:
			idx = 0;
			while (!isFuncCall(block.getStatement(idx), "sugarExprs"))
				idx ++;
			CtStatement s = block.getStatement(idx);
			if (s instanceof CtInvocation) {
				CtInvocation<?> inv = (CtInvocation<?>) s;
				root = new MyCtNode(inv.getArguments(), false);
			}
			else 
				throw new InvalidRuleException("TopLevelSig is expression, but the first statement is not sugarExprs(), it is: " + s.toString());
			break;
		case ExprSig:
			idx = 0;
			while (!isFuncCall(block.getStatement(idx), "sugarExprs"))
				idx ++;
			s = block.getStatement(idx);
			if (s instanceof CtInvocation) {
				CtInvocation<?> inv = (CtInvocation<?>) s;
				root = new MyCtNode(inv.getArguments().get(0), false);
			}
			else 
				throw new InvalidRuleException("TopLevelSig is expression, but the first statement is not sugarExprs(), it is: " + s.toString());
		}
		
		// Now I need to convert root to TransAST
		TransASTNode ret = convertToTransAST(schema, r, root);
		switch (r.topSig) {
		case ColCodeEleSig:
			ret.setNodeSig(new MyNodeSig(CtCodeElementImpl.class, true));
			break;
		case CodeEleSig:
			ret.setNodeSig(new MyNodeSig(CtCodeElementImpl.class, false));
			break;
		case ColExprSig:
			ret.setNodeSig(new MyNodeSig(CtExpressionImpl.class, true));
			break;
		case ExprSig:
			ret.setNodeSig(new MyNodeSig(CtExpressionImpl.class, false));
			break;
		}
		return ret;
	}

	private static TransASTNode convertToTransAST(TransformSchema schema, ManualTransformRule r, MyCtNode root) {
		if (root.isCollection()) {
			int m = root.getNumChildren();
			if (m > 0) {
				MyCtNode child0 = root.getChild(0);
				// Handle ASTColVar
				if (child0.isEleClass(CtInvocationImpl.class)) {
					CtInvocationImpl<?> inv = (CtInvocationImpl<?>) child0.getRawObject();
					String fname = inv.getExecutable().toString();
					if (fname.contains("#ASTColVar")) {
						CtLiteral<?> lit = (CtLiteral<?>)inv.getArguments().get(0);
						Integer idx = (Integer) lit.getValue();
						return schema.createFreeVar(idx);
					}
				}
			}
			List<TransASTNode> children = new ArrayList<TransASTNode>();
			for (int i = 0; i < m; i++) {
				children.add(convertToTransAST(schema, r, root.getChild(i)));
			}
			return schema.createCollection(root.nodeSig(), children);
		}
		else if (root.isTrait()) {
			return schema.createTrait(root.nodeSig(), root.nodeTrait());
		}
		else if (root.isReference()) {
			// To deal with special references in rules
			if (!root.isEleClass(CtTypeReference.class)) {
				if (root.isEleClass(CtVariableReference.class)) {
					String vname = root.toString();
					if (vname.contains("ASTVar_") && vname.length() > 7) {
						int idx = Integer.parseInt(vname.substring(7));
						return schema.createFreeVar(idx);
					}
					else if (root.isEleClass(CtFieldReference.class)) {
						// A special case for length access
						CtFieldReference<?> fref = (CtFieldReference<?>) root.getRawObject();
						String fname = fref.getSimpleName();
						if (fname.equals("length")) {
							TransASTFreeVar fvar = schema.createFreshFreeVar(root.nodeSig());
							r.vargens.put(fvar.vid, new ReferenceGenerator(
										root.nodeSig(),
										ReferenceGenerator.ReferenceScopeKind.NONE,
										ReferenceGenerator.ReferenceScopeKind.BINDING,
										ReferenceGenerator.ReferenceScopeKind.NONE,
										"length",
										false
									));
							return fvar;
						}
					}
				}
				if (root.isEleClass(CtExecutableReference.class)) {
					String ename = root.toString();
					int eidx = ename.indexOf("#ASTERefVar_");
					if (eidx != -1) {
						int eidx2 = ename.indexOf("(", eidx);
						if (eidx2 != -1) {
							int idx = Integer.parseInt(ename.substring(eidx + 12, eidx2));
							return schema.createFreeVar(idx);
						}
					}
					else {
						TransASTFreeVar fvar = schema.createFreshFreeVar(root.nodeSig());
						CtExecutableReference<?> eref = (CtExecutableReference<?>)root.getRawObject();
						r.vargens.put(fvar.vid, new ReferenceGenerator(
									root.nodeSig(),
									ReferenceGenerator.ReferenceScopeKind.BINDING,
									ReferenceGenerator.ReferenceScopeKind.NONE,
									ReferenceGenerator.ReferenceScopeKind.NONE,
									eref.getSimpleName(),
									false
								));
						return fvar;
					}
				}
				throw new InvalidRuleException("Reference is not abstracted away? Invalid rule! Reference: " + root.toString());
			}
			else
				return schema.createFreshFreeVar(root.nodeSig());
		}
		else {
			if (root.isEleClass(CtInvocationImpl.class)) {
				CtInvocationImpl<?> inv = (CtInvocationImpl<?>) root.getRawObject();
				String fname = inv.getExecutable().toString();
				if (fname.contains("#ASTVar")) {
					CtLiteral<?> lit = (CtLiteral<?>)inv.getArguments().get(0);
					Integer idx = (Integer) lit.getValue();
					return schema.createFreeVar(idx);
				}
			}
			int m = root.getNumChildren();
			Map<String, TransASTNode> children = new HashMap<String, TransASTNode>();
			for (int i = 0; i < m; i++)
				children.put(root.getChildName(i), convertToTransAST(schema, r, root.getChild(i)));
			return schema.createCtEle(root.nodeSig(), children);
		}
	}
}
