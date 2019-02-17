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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mysql.cj.api.x.Collection;

import genesis.Config;
import genesis.GenesisException;
import genesis.analysis.StaticAnalyzer;
import genesis.analysis.TypeHelper;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.schema.TransformSchema.PostTreeBuilder;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.reflect.code.CtReturnImpl;
import spoon.support.reflect.reference.CtLocalVariableReferenceImpl;
import spoon.support.reflect.reference.CtParameterReferenceImpl;

/* This class implements APIs that use TransformSchema to
   transform spoon model tree */
public class SchemaAdapter {

	private TransformSchema schema;
	private HashSet<Integer> vinpre, vinpost;
	private HashMap<Integer, MyCtNode> varBindings;
	private MyCtNode appliedTree;
	private MyCtNode res;
	private boolean passTypecheck;
	private Factory spoonf;
	// This is to avoid too many deepClone
	private LoadingCache<MyCtNode, MyCtNode> cloneCache;
	
	private boolean allowCast;

	public SchemaAdapter(Factory spoonf, TransformSchema schema) {
		this.spoonf = spoonf;
		this.schema = schema;
		this.vinpre = schema.varsInPre();
		this.vinpost = schema.varsInPost();
		this.varBindings = new HashMap<Integer, MyCtNode>();
		this.appliedTree = null;
		this.res = null;
		this.passTypecheck = true;
		this.allowCast = true;
		this.cloneCache = null;
	}
	
	public void initCloneCache() {
		this.cloneCache = CacheBuilder.newBuilder().maximumSize(1024 * 1024).weakKeys().build(
				new CacheLoader<MyCtNode, MyCtNode>() { 
					public MyCtNode load(MyCtNode a) {
						return a.deepClone();
					}
				});
	}
	
	public void disableCloneCache() {
		this.cloneCache = null;
	}

	static boolean checkSigSet(Set<MyNodeSig> a, Set<MyNodeSig> b) {
		HashSet<MyNodeSig> tmp = new HashSet<MyNodeSig>(a);
		for (MyNodeSig sig : b) {
			Iterator<MyNodeSig> it = tmp.iterator();
			while (it.hasNext()) {
				MyNodeSig sig2 = it.next();
				if (sig2.isSuperOrEqual(sig))
					it.remove();
			}
		}
		return tmp.size() == 0;
	}

	public boolean checkInside(Set<MyNodeSig> inside) {
		return checkSigSet(schema.inside, inside);
	}

	class TransASTMatcher extends TransASTVisitor {
		Stack<MyCtNode> stack;
		HashMap<Integer, MyCtNode> varBindings;

		TransASTMatcher(MyCtNode root) {
			this.stack = new Stack<MyCtNode>();
			this.stack.push(root);
			this.varBindings = new HashMap<Integer, MyCtNode>();
		}

		TransASTMatcher(MyCtNode root, HashMap<Integer, MyCtNode> bindings) {
			this.stack = new Stack<MyCtNode>();
			this.stack.push(root);
			this.varBindings = bindings;
		}

		@Override
		public boolean visitTrait(TransASTTrait n) {
			MyCtNode cur = stack.peek();
			if (!cur.isTrait()) 
				return false;
			if (!cur.nodeSig().equals(n.nodeSig)) 
				return false;
			return cur.nodeTrait().equals(n.trait);
		}

		@Override
		public boolean visitFreeVar(TransASTFreeVar n) {
			MyCtNode cur = stack.peek();
			boolean pass = false;
			// XXX: So far in genesis we do not separate these two.
			if (n.nodeSig.isClass(CtLocalVariableReferenceImpl.class, false) && 
				cur.nodeSig().isClass(CtParameterReferenceImpl.class, false))
				pass = true;
			if (n.nodeSig.isClass(CtParameterReferenceImpl.class, false) &&
				cur.nodeSig().isClass(CtLocalVariableReferenceImpl.class, false))
				pass = true;
			if (!n.nodeSig.isSuperOrEqual(cur.nodeSig()) && !pass) 
				return false;
			if (!varBindings.containsKey(n.vid))
				varBindings.put(n.vid, cur);
			else {
				if (!cur.treeEquals(varBindings.get(n.vid)))
					return false;
			}
			return true;
		}

		@Override
		public boolean visitCollection(TransASTCollection n) {
			MyCtNode cur = stack.peek();
			if (!cur.isCollection()) 
				return false;
			if (!n.nodeSig.isSuperOrEqual(cur.nodeSig())) 
				return false;
			if (cur.getNumChildren() != n.children.size()) 
				return false;
			return true;
		}

		@Override
		public boolean visitCtEle(TransASTCtEle n) {
			MyCtNode cur = stack.peek();
			if (cur.isTrait() || cur.isCollection() || cur.isReference()) 
				return false;
			if (!n.nodeSig.equals(cur.nodeSig())) 
				return false;
			if (cur.getNumChildren() != n.children.size()) 
				return false;
			return true;
		}

		@Override
		public boolean scanCollection(TransASTCollection n) {
			boolean ret = visitCollection(n) && visitNode(n);
			if (!ret) 
				return false;
			int m = n.children.size();
			MyCtNode cur = stack.peek();
			for (int i = 0; i < m; i++) {
				stack.push(cur.getChild(i));
				ret = scanNode(n.children.get(i));
				if (!ret) 
					return false;
				stack.pop();
			}
			return true;
		}

		@Override
		public boolean scanCtEle(TransASTCtEle n) {
			boolean ret = visitCtEle(n) && visitNode(n);
			if (!ret) 
				return false;
			MyCtNode cur = stack.peek();
			for (String name : n.children.keySet()) {
				stack.push(cur.getChild(name));
				ret = scanNode(n.children.get(name));
				if (!ret) 
					return false;
				stack.pop();
			}
			return true;
		}

		HashMap<Integer, MyCtNode> getResult() {
			return varBindings;
		}
	}

	/** This function resets all existing var bindings and apply the schema to a
	 * new tree
	 * @param root
	 * @return
	 */
	public boolean applyTo(MyCtNode root) {
		appliedTree = root;
		TransASTMatcher v = new TransASTMatcher(root);
		boolean ret = v.scanNode(schema.pre);
		if (ret) {
			varBindings = v.getResult();
			if (Config.enableVarContains) {
				for (Entry<Integer, MyCtNode> e : varBindings.entrySet()) {
					int vid = e.getKey();
					Set<MyNodeSig> sigs = schema.varContains.get(vid);
					if (sigs == null) continue;
					Set<MyNodeSig> sigsInVar = MyNodeSig.canonicalSigSet(e.getValue().nodeSigSet());
					sigs.removeAll(sigsInVar);
					// XXX: We don't distinguish CtLocalVariableReferenceImpl and CtParameterImpl
					if (sigsInVar.contains(new MyNodeSig(CtLocalVariableReferenceImpl.class, false)) ||
							sigsInVar.contains(new MyNodeSig(CtParameterReferenceImpl.class, false))) {
						sigs.remove(new MyNodeSig(CtLocalVariableReferenceImpl.class, false));
						sigs.remove(new MyNodeSig(CtParameterReferenceImpl.class, false));
					}
					if (sigs.size() != 0) 
						return false;
				}
			}
		}
		
		res = null;
		return ret;
	}

	public void setVarBinding(Integer vid, MyCtNode tree) {
		if (!vinpost.contains(vid))
			throw new IllegalArgumentException("vid has to be in the post tree");
		varBindings.put(vid, tree);
		res = null;
	}

	public void setVarBindings(Map<Integer, MyCtNode> varb) {
		for (Integer v : varb.keySet()) {
			setVarBinding(v, varb.get(v));
		}
		res = null;
	}

	public MyCtNode getVarBinding(Integer vid) {
		if (varBindings.containsKey(vid))
			return varBindings.get(vid);
		else
			return null;
	}

	public HashMap<Integer, MyCtNode> getVarBindings() {
		return varBindings;
	}

	public MyCtNode getTransformedTree() {
		if (res == null)
			computeTransformedTree();
		return res;
	}

	public boolean passTypecheck() {
		return passTypecheck;
	}

	private boolean checkReplaceable(MyCtNode before, MyCtNode res) {
		// XXX: Ideally, the type information should be included at 
		// VarTypeContext so that we can kill many of those at 
		// VarGeneration stages
		
		// XXX: I decide to take a case by case approach here instead of
		// merging this with the shit in the TypeHelper. Maybe one day I
		// should rewrite it with a more systematic type checker, but not
		// for now
		if (res.isNull()) return true;
		
		if (res.isTrait()) {
			assert(before.isTrait());
			if (Collection.class.isAssignableFrom(before.nodeTrait().getClass()))
				return Collection.class.isAssignableFrom(res.nodeTrait().getClass());
			else
				return before.nodeTrait().getClass().equals(res.nodeTrait().getClass());
		}
		// Now it is an element the main case
		MyCtNode parentNode = before.parentEleNode();
		// If the original tree expects a statement, we need to give a statement
		// not an expression
		if (before.isStatementFieldOnly()) {
			if (res.isCollection()) {
				int n = res.getNumChildren();
				for (int i = 0; i < n; i++) {
					MyCtNode child = res.getChild(i);
					if (!child.isEleClass(CtStatement.class))
						return false;
				}
			}
			else { 
				if (!res.isEleClass(CtStatement.class))
					return false;
			}
		}
		if (parentNode.isEleClass(CtReturn.class)) {
			CtReturn<?> ret = (CtReturn<?>) parentNode.getRawObject(); 
			if (ret.getReturnedExpression() == before.getRawObject()) {
				// It is an returned expression, it needs to respect the return type
				Object o = res.getRawObject();
				if (o instanceof CtExpression) {
					StaticAnalyzer ana = new StaticAnalyzer(before);
					if (!TypeHelper.typeCompatible(ana.getEnclosingFuncReturnType(), ((CtExpression<?>) o).getType()))
						return false;
				}
				else
					return false;
			}
		}
		
		if (parentNode.isEleClass(CtLocalVariable.class)) {
			CtLocalVariable<?> lvar = (CtLocalVariable<?>) parentNode.getRawObject();
			Object o = res.getRawObject();
			// You replaced the right hand side
			if (lvar.getAssignment() == before.getRawObject()) {
				if (o instanceof CtExpression) {
					if (!TypeHelper.typeCompatible(lvar.getType(), ((CtExpression<?>) o).getType()))
						return false;
				}
				else
					return false;
			}
		}
		
		if (parentNode.isEleClass(CtAssignment.class)) {
			CtAssignment<?, ?> assign = (CtAssignment<?, ?>) parentNode.getRawObject();
			Object o = res.getRawObject();
			// You replaced the right hand side
			if (assign.getAssignment() == before.getRawObject()) {
				if (o instanceof CtExpression) {
					if (!TypeHelper.typeCompatible(assign.getAssigned().getType(), ((CtExpression<?>) o).getType()))
						return false;
				}
				else
					return false;
			}
			// replaced the left hand side
			if (assign.getAssigned() == before.getRawObject()) {
				if (o instanceof CtVariableAccess) {
					if (!TypeHelper.typeCompatible(((CtExpression<?>) o).getType(), assign.getAssignment().getType()))
						return false;
				}
				else
					return false;
			}
		}
		
		if (parentNode.isEleClass(CtAbstractInvocation.class)) {
			CtAbstractInvocation<?> inv = (CtAbstractInvocation<?>) parentNode.getRawObject();
			if (inv.getArguments() == before.getRawObject()) {
				// The whole argument list is the target, let's check all
				Object o = res.getRawObject();
				if (o instanceof List) {
					@SuppressWarnings("unchecked")
					List<Object> c = (List<Object>) o;
					for (int i = 0; i < c.size(); i++) {
						Object o1 = c.get(i);
						if (o1 instanceof CtExpression) {
							CtExpression<?> rese = (CtExpression<?>) o1;
							if (!checkArgumentCompatible(inv, i, rese))
								return false;
						}
						else
							return false;
					}
				}
				else
					return false;
			}
			else {
				int argCnt = -1;
				List<CtExpression<?>> args = inv.getArguments();
				for (int i = 0; i < args.size(); i++) {
					CtExpression<?> e = args.get(i);
					if (before.getRawObject() == e) {
						argCnt = i;
						break;
					}
				}
				if (argCnt != -1) {
					// OK, this is one argument case 
					Object o = res.getRawObject();
					if (!(o instanceof CtExpression<?>))
						return false;
					CtExpression<?> rese = (CtExpression<?>) o;				
					if (!checkArgumentCompatible(inv, argCnt, rese))
						return false;
				}
			}
		}
		
		// If and while it must be condition
		if (parentNode.isEleClass(CtIf.class) || parentNode.isEleClass(CtWhile.class)) {
			CtExpression<?> cond = null;
			Object rawo = parentNode.getRawObject(); 
			if (rawo instanceof CtIf)
				cond = ((CtIf) rawo).getCondition();
			else if (rawo instanceof CtWhile) {
				cond = ((CtWhile) rawo).getLoopingExpression();
			}
			if (cond == before.getRawObject()) {
				if (!(res.getRawObject() instanceof CtExpression)) return false;
				if (!TypeHelper.getExprType(res).equals(res.getFactory().Type().BOOLEAN) &&
					!TypeHelper.getExprType(res).equals(res.getFactory().Type().BOOLEAN_PRIMITIVE))
					return false;
			}
		}
		
		if (parentNode.isEleClass(CtBinaryOperator.class)) {
			CtBinaryOperator<?> binop = (CtBinaryOperator<?>) parentNode.getRawObject();
			boolean isLeft = binop.getLeftHandOperand() == before.getRawObject();
			boolean isRight = binop.getRightHandOperand() == before.getRawObject(); 
			if (isLeft || isRight) {
				if (!(res.getRawObject() instanceof CtExpression)) return false;
				CtTypeReference<?> lt = binop.getLeftHandOperand().getType();
				CtTypeReference<?> rt = binop.getRightHandOperand().getType();				
				CtTypeReference<?> nt = TypeHelper.getExprType(res);
				if (isLeft)
					lt = nt;
				else
					rt = nt;
				Factory f = before.getFactory();
				boolean ok = false;
				if (binop.getKind() == BinaryOperatorKind.INSTANCEOF) {
					ok = true;
				}
				if (binop.getKind() == BinaryOperatorKind.PLUS)
					if ((lt.equals(f.Type().STRING) || rt.equals(f.Type().STRING))) {
						ok = true;
					}
				if (!ok)
					if (TypeHelper.isRelational(binop.getKind())) {
						// Null cannot compare to primitive, no way
						if ((lt.equals(f.Type().nullType()) && rt.isPrimitive()) ||
							(rt.equals(f.Type().nullType()) && lt.isPrimitive())) {
							return false;
						}
					}
				if (!ok)
					if (!TypeHelper.typeCompatible(lt, rt) && !TypeHelper.typeCompatible(rt, lt)) {
						return false;
					}
			}
		}
		
		if (parentNode.isEleClass(CtInvocation.class) || parentNode.isEleClass(CtFieldAccess.class)) {
			CtTargetedExpression<?, ?> texp = (CtTargetedExpression<?, ?>) parentNode.getRawObject();
			if (texp.getTarget() == before.getRawObject()) {
				// OK you the target, better to check you have the method
				Object o = res.getRawObject();
				if (!(o instanceof CtExpression<?>))
					return false;
				CtExpression<?> rese = (CtExpression<?>) o;
				if (rese.getType().isPrimitive())
					return false;
				if (texp instanceof CtInvocation) {
					if (!TypeHelper.typeCompatible(((CtInvocation<?>) texp).getExecutable().getDeclaringType(), rese.getType()))
						return false;
				}
				else if (texp instanceof CtFieldAccess) {
					if (!TypeHelper.typeCompatible(((CtFieldAccess<?>) texp).getVariable().getDeclaringType(), rese.getType()))
						return false;
				}
			}
		}
		
		MyCtNode parentBlock = before;
		while (parentBlock != null && !parentBlock.isEleClass(CtStatementList.class)) {
			parentBlock = parentBlock.parentEleNode();
		}
		
		// XXX: Right now I am going to just prevent any change that removes local variable
		// declaration. The intuition is that such changes almost always break programs and 
		// it is very unlikely to bring any good patch.
		// Checking for both top level only and full nested!!
		HashSet<String> declVars = getAllDecledLocalVars(before, true);
		HashSet<String> declVars1 = getAllDecledLocalVars(res, true);
		declVars.removeAll(declVars1);
		if (declVars.size() != 0) {
			if (parentBlock != null) {
				// It is fine to remove a local variable declaration only if it only appears 
				// in the before tree not elsewhere
				for (String vname : declVars) {
					if (countLocalRef(parentBlock, vname) != countLocalRef(before, vname))
						return false;
				}
			}
		}
		declVars1 = getAllDecledLocalVars(res, false);
		declVars1.addAll(declVars);
		
		// Then I am going to make sure this code does not reference any local variable before
		// it defines the local variable
		final HashSet<String> bannedVar = declVars1;
		final List<Boolean> resHolder = new ArrayList<Boolean>();
		resHolder.add(true);
		// I know this is more permissive than it should be, but whatever
		CtScanner localVarScanner = new CtScanner() {
			@SuppressWarnings("rawtypes")
			CtLocalVariableImpl inDecl = null;
			
			@SuppressWarnings("rawtypes")
			@Override
			public void enter(CtElement ele) {
				if (ele instanceof CtLocalVariableImpl) {
					bannedVar.remove(((CtLocalVariableImpl) ele).getSimpleName());
					inDecl = (CtLocalVariableImpl) ele;
				}
			}
			
			@Override
			public void exit(CtElement ele) {
				if (ele instanceof CtLocalVariableImpl) {
					if (inDecl == ele)
						inDecl = null;
				}
			}
			
			@Override
			public void enterReference(CtReference ref) {
				if (inDecl != null) return;
				if (ref instanceof CtLocalVariableReference) {
					if (bannedVar.contains(ref.getSimpleName()))
						resHolder.clear();
				}
			}
		};
		res.acceptScanner(localVarScanner);
		if (resHolder.size() == 0)
			return false;
		
		// XXX: This is a common case which triggers missing return statement error
		if (parentBlock != null) {
			MyCtNode grandPa = parentBlock.parentEleNode();
			if (grandPa != null && grandPa.isEleClass(CtExecutable.class)) {
				if (isLastReturnValStatement(before) && !isLastReturnValStatement(res))
					return false;
			}
		}
	
		return true;
	}
	
	@SuppressWarnings("rawtypes")
	private boolean isLastReturnValStatement(CtElement n) {
		if (n instanceof CtReturnImpl) {
			return ((CtReturnImpl) n).getReturnedExpression() != null;
		}
		else if (n instanceof CtIfImpl) {
			CtElement thenE = ((CtIfImpl) n).getThenStatement();
			CtElement elseE = ((CtIfImpl) n).getElseStatement();
			return isLastReturnValStatement(thenE) &&
					elseE != null && isLastReturnValStatement(elseE);
		}
		else if (n instanceof CtBlockImpl) {
			List<CtStatement> l = ((CtStatementList) n).getStatements();
			if (l.size() > 0) {
				CtElement lastStmt = l.get(l.size() - 1);
				return isLastReturnValStatement(lastStmt);
			}
			else
				return false;
		}
		else
			return false;
	}
	
	private boolean isLastReturnValStatement(MyCtNode n) {
		if (n.isCollection()) {
			int m = n.getNumChildren();
			if (m == 0) return false;
			MyCtNode last = n.getChild(m - 1);
			return isLastReturnValStatement(last);
		}
		else if (!n.isReference() && !n.isTrait())
			return isLastReturnValStatement((CtElement) n.getRawObject());
		else
			return false;
	}
	
	private int countLocalRef(MyCtNode n, String vname) {
		final List<Integer> holder = new ArrayList<Integer>();
		holder.add(0);
		CtScanner scanner = new CtScanner() {
			 @Override
			 public void enterReference(CtReference r) {
				 if (r instanceof CtLocalVariableReference) {
					 if (vname.equals(r.getSimpleName()))
						 holder.set(0, holder.get(0) + 1);
				 }
			 }
		};
		n.acceptScanner(scanner);
		return holder.get(0);
	}

	private HashSet<String> getAllDecledLocalVars(MyCtNode n, final boolean ignoreNested) {
		final HashSet<String> ret = new HashSet<String>();
		CtScanner scan = new CtScanner() {
			
			CtStatementList disabledIn;
			
			@SuppressWarnings("rawtypes")
			@Override
			public void enter(CtElement ele) {
				if (disabledIn == null && ignoreNested) {
					if (ele instanceof CtStatementList)
						disabledIn = (CtStatementList) ele;
				}
				if (disabledIn != null) return;
				if (ele instanceof CtLocalVariableImpl) {
					ret.add(((CtLocalVariableImpl) ele).getSimpleName());
				}
			}
			
			@Override
			public void exit(CtElement ele) {
				if (disabledIn != null)
					if (disabledIn == ele)
						disabledIn = null;
			}
		};
		n.acceptScanner(scan);
		return ret;
	}
	
	private boolean checkArgumentCompatible(CtAbstractInvocation<?> inv, int argCnt, CtExpression<?> rese) {
		CtExecutableReference<?> eref = inv.getExecutable();
		boolean compatible = false;
		List<CtTypeReference<?>> tpargs = eref.getParameters();
		if (argCnt >= tpargs.size() - 1) {
			CtTypeReference<?> lastTArg = tpargs.get(tpargs.size() - 1);
			if (lastTArg instanceof CtArrayTypeReference) {
				if (TypeHelper.typeCompatible(((CtArrayTypeReference<?>) lastTArg).getComponentType(), rese.getType()))
					compatible = true;
			}
		}
		
		if (!compatible && argCnt < tpargs.size()) {
			if (TypeHelper.typeCompatible(tpargs.get(argCnt), rese.getType()))
				compatible = true;
		}
		return compatible;
	}

	private void computeTransformedTree() {
		// We are going to allow inferable type var not passed!
		if (!varBindings.keySet().containsAll(vinpre))
			throw new GenesisException("Must provide all bindings befofre computing the transformed tree!");
		HashMap<Integer, MyCtNode> tmp = new HashMap<Integer, MyCtNode>(varBindings);
		//boolean infer = false;
		for (Integer vid : vinpost) {
			if (!varBindings.containsKey(vid)) {
				if (schema.isInferableTypeVid(vid)) {
					tmp.put(vid, new MyCtNode(null, true));
					//infer = true;
				}
				else
					throw new GenesisException("Must provide all bindings befofre computing the transformed tree!");
			}
		}

		PostTreeBuilder b  = null;
		if (cloneCache != null)
			b = new PostTreeBuilder(spoonf, tmp, cloneCache);
		else
			b = new PostTreeBuilder(spoonf, tmp);
		
		b.scanNode(schema.post);
		res = b.getResult(schema.post);
		if (res == null) return;

		if (!res.isTrait())
			passTypecheck = TypeHelper.inferAllTypesIn(res, allowCast);
		else
			passTypecheck = true;
		
		if (passTypecheck) {
			passTypecheck = checkReplaceable(appliedTree, res);
		}
	}

	public void clearAllPostVarBinding() {
		Iterator<Map.Entry<Integer,MyCtNode>> it = varBindings.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, MyCtNode> pair = it.next();
			if (!vinpre.contains(pair.getKey()))
				it.remove();
		}
	}

	public HashMap<Integer, MyCtNode> matchWith(MyCtNode n) {
		HashMap<Integer, MyCtNode> tmp = new HashMap<Integer, MyCtNode>(varBindings);
		TransASTMatcher v = new TransASTMatcher(n, tmp);
		boolean res = v.scanNode(schema.post);
		if (res == false)
			return null;

		Iterator<Map.Entry<Integer, MyCtNode>> it = tmp.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, MyCtNode> pair = it.next();
			if (varBindings.containsKey(pair.getKey()))
				it.remove();
		}
		return tmp;
	}

	public VarTypeContext getVarTypeContext(TransformSchema schema, Integer vid) {
		return schema.getVarTypeContext(spoonf, vid, varBindings);
	}

	public void setAllowCast(boolean allowCast) {
		this.allowCast = allowCast;
	}
}
