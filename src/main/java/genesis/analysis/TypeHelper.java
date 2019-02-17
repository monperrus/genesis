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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Level;

import genesis.GenesisException;
import genesis.node.MyCtNode;
import spoon.Launcher;
import spoon.reflect.binding.CtTypeBinding;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtArrayWrite;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtOperatorAssignment;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtExecutableReferenceExpressionImpl;
import spoon.support.reflect.code.CtLambdaImpl;
import spoon.support.reflect.reference.SpoonClassNotFoundException;

public class TypeHelper {

	static class TypeScanner extends CtScanner {
		boolean succ;
		boolean allowCast;

		public TypeScanner(boolean allowCast) {
			this.allowCast = allowCast; 
			succ = true;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public void exit(CtElement ele) {
			if (!succ) return;
			if (ele instanceof CtExpression) {
				CtExpression exp = (CtExpression) ele;
				if (exp.getTypeCasts().size() != 0 && exp.getTypeCasts().get(0) != null) {
					List<CtTypeReference<?>> casts = exp.getTypeCasts();
					// Seems this works conveniently
					exp.setType(casts.get(casts.size() - 1));
				}
				else {
					if (exp.getTypeCasts().size() != 0 && exp.getTypeCasts().get(0) == null)
						exp.setTypeCasts(new ArrayList<CtTypeReference>());
					boolean ret = true;
					if (ele instanceof CtLiteral)
						ret = inferType((CtLiteral<?>) ele);
					else if (ele instanceof CtOperatorAssignment)
						ret = inferType((CtOperatorAssignment<?, ?>) ele, allowCast);
					else if (ele instanceof CtAssignment)
						ret = inferType((CtAssignment<?, ?>) ele, allowCast);
					else if (ele instanceof CtVariableAccess)
						ret = inferType((CtVariableAccess<?>) ele, allowCast);
					else if (ele instanceof CtUnaryOperator)
						ret = inferType((CtUnaryOperator<?>) ele);
					else if (ele instanceof CtInvocation) {
						if (ele.toString().contains("builder(tree, pattern)"))
							ret = true;
						ret = inferType((CtInvocation<?>) ele, allowCast);
					}
					else if (ele instanceof CtNewClass)
						ret = inferType((CtNewClass<?>) ele, allowCast);
					else if (ele instanceof CtArrayWrite)
						ret = inferType((CtArrayWrite<?>) ele);
					else if (ele instanceof CtArrayRead)
						ret = inferType((CtArrayRead<?>) ele);
					else if (ele instanceof CtBinaryOperator)
						ret = inferType((CtBinaryOperator<?>)ele);
					else if (ele instanceof CtConditional)
						ret = inferType((CtConditional<?>) ele);
					else if (ele instanceof CtConstructorCall)
						ret = inferType((CtConstructorCall<?>) ele, allowCast);
					else if (ele instanceof CtTypeAccess) {
						if (((CtExpression) ele).getType() == null) {
							// XXX: This is strange but if we can fix it via looking for its parent, we are going to do it
							// Maybe a better solution is to take a look inside the spoon to find out why this happens
							CtElement parentEle = ele.getParent();
							if (parentEle instanceof CtInvocation) {
								((CtExpression) ele).setType(((CtInvocation) parentEle).getExecutable().getDeclaringType());
							}
							else if (parentEle instanceof CtFieldAccess) {
								((CtExpression) ele).setType(((CtFieldAccess) parentEle).getVariable().getDeclaringType());
							}
							else
								throw new GenesisException("Uninitialized typeaccess!");
						}
					}
					else if (ele instanceof CtThisAccess || ele instanceof CtAnnotation || ele instanceof CtNewArray) {
						if (((CtTypedElement) ele).getType() == null) {
							//FIXME: Not sure why this happens, check it after deadline
							succ = false;
							//throw new GenesisException("Uninitialized typed access: " + ele.toString());
						}
					}
					else if (ele instanceof CtLambdaImpl) {
						if (((CtTypedElement) ele).getType() == null)
							throw new GenesisException("Unassigned type for a CtLambdaImpl expr: " + ele.toString());
					}
					else if (ele instanceof CtExecutableReferenceExpressionImpl) {
						if (((CtTypedElement) ele).getType() == null)
							throw new GenesisException("Unassigned type for a CtExecutableReferenceExpressionImpl expr: " + ele.toString());
					}
					else 
						throw new GenesisException("Unhandled type inference for an expr: " + ele.toString());
					if (!ret) {
						succ = false;
					}
				}
			}
		}

		@Override
		public void exitReference(CtReference ref) {
			// XXX: Maybe some reference will have type problem due to spoon bug
		}

		public boolean getResult() {
			return succ;
		}
	}

	// This may get accessed in Multi-thread way
	private static ConcurrentHashMap<String, Boolean> compCache;
	//private static long cnt1, cnt2;

	static {
		compCache = new ConcurrentHashMap<String, Boolean>();
		//cnt1 = 0;
		//cnt2 = 0;
	}

	public static boolean inferAllTypesIn(MyCtNode root, boolean allowCast) {
		TypeScanner s = new TypeScanner(allowCast);
		root.acceptScanner(s);
		return s.getResult();
	}

	public static boolean inferAllTypesIn(MyCtNode root) {
		return inferAllTypesIn(root, false);
	}

	public static boolean isRelational(BinaryOperatorKind k) {
		return (k == BinaryOperatorKind.EQ || k == BinaryOperatorKind.NE || k == BinaryOperatorKind.GT ||
				k == BinaryOperatorKind.GE || k == BinaryOperatorKind.LT || k == BinaryOperatorKind.LE);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean inferType(CtBinaryOperator<?> binop) {
		CtBinaryOperator n = binop;
		CtTypeReference<?> lt = n.getLeftHandOperand().getType();
		CtTypeReference<?> rt = n.getRightHandOperand().getType();
		Factory f = n.getFactory();
		
		// XXX: I am not going to deal with instanceof
		if (binop.getKind() == BinaryOperatorKind.INSTANCEOF) {
			n.setType(f.Type().BOOLEAN_PRIMITIVE);
			return true;
		}
		
		// For instanceof, these could be null, but should not be null for the rest
		if (lt == null || rt == null)
			return false;
		
		if (binop.getKind() == BinaryOperatorKind.AND || binop.getKind() == BinaryOperatorKind.OR) {
			if (!lt.equals(f.Type().BOOLEAN) && !lt.equals(f.Type().BOOLEAN_PRIMITIVE))
				return false;
			if (!rt.equals(f.Type().BOOLEAN) && !rt.equals(f.Type().BOOLEAN_PRIMITIVE))
				return false;
		}
		
		if (binop.getKind() == BinaryOperatorKind.PLUS)
			if (lt.equals(f.Type().STRING) || rt.equals(f.Type().STRING)) {
				n.setType(f.Type().STRING);
				return true;
			}
		
		if (isRelational(n.getKind())) {
			// Null cannot compare to primitive, no way
			if ((lt.equals(f.Type().nullType()) && rt.isPrimitive()) ||
				(rt.equals(f.Type().nullType()) && lt.isPrimitive())) {
				return false;
			}
		}
		
		if (typeCompatible(lt, rt)) {
			if (isRelational(n.getKind()))
				n.setType(f.Type().BOOLEAN_PRIMITIVE);
			else
				n.setType(lt);
		}
		else if (typeCompatible(rt, lt))
			if (isRelational(n.getKind()))
				n.setType(f.Type().BOOLEAN_PRIMITIVE);
			else
				n.setType(rt);
		else
			return false;
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtConditional<?> cond) {
		CtConditional n = cond;
		CtTypeReference<?> ctype = n.getCondition().getType();
		CtTypeReference<?> lt = n.getThenExpression().getType();
		CtTypeReference<?> rt = n.getElseExpression().getType();
		if (!typeEquals(ctype, "boolean"))
			return false;
		if (typeCompatible(lt, rt))
			n.setType(lt);
		else if (typeCompatible(rt, lt))
			n.setType(rt);
		else
			return false;
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtLiteral<?> lit) {
		CtLiteral n = lit;
		Factory f = n.getFactory();
		Object v = n.getValue();
		if (v instanceof Void)
			n.setType(f.Type().VOID_PRIMITIVE);
		else if (v instanceof Integer)
			n.setType(f.Type().INTEGER_PRIMITIVE);
		else if (v instanceof Long)
			n.setType(f.Type().LONG_PRIMITIVE);
		else if (v instanceof Float)
			n.setType(f.Type().FLOAT_PRIMITIVE);
		else if (v instanceof Double)
			n.setType(f.Type().DOUBLE_PRIMITIVE);
		else if (v instanceof String)
			n.setType(f.Type().STRING);
		else if (v instanceof Boolean)
			n.setType(f.Type().BOOLEAN_PRIMITIVE);
		else if (v instanceof Character)
			n.setType(f.Type().CHARACTER_PRIMITIVE);
		else if (v == null)
			n.setType(f.Type().nullType());
		else if (v instanceof CtTypeReference) {
			// INSTNACEOF righthand operand has null type, I am not going to change it
		}
		else {
			throw new GenesisException("Unknown literal type!");
		}
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtOperatorAssignment<?, ?> oass, boolean allowCast) {
		CtOperatorAssignment n = oass;
		CtTypeReference<?> lt = n.getAssigned().getType();
		CtTypeReference<?> rt = n.getAssignment().getType();
		if (!typeCompatible(lt, rt)) {
			if (allowCast && typeCompatible(rt, lt)) {
				List<CtTypeReference> newc = new ArrayList<CtTypeReference>();
				newc.add(lt);
				n.getAssignment().setTypeCasts(newc);
			}
			else {
				return false;
			}
		}
		n.setType(lt);
		return true;
	}
	
	public static boolean inferType(CtOperatorAssignment<?, ?> oass) {
		return inferType(oass, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtAssignment<?, ?> ass, boolean allowCast) {
		CtAssignment n = ass;
		CtTypeReference<?> lt = n.getAssigned().getType();
		CtTypeReference<?> rt = n.getAssignment().getType();
		if (!typeCompatible(lt, rt)) {
			if (allowCast && typeCompatible(rt, lt)) {
				List<CtTypeReference> newc = new ArrayList<CtTypeReference>();
				newc.add(lt);
				n.getAssignment().setTypeCasts(newc);
			}
			else {
				return false;
			}
		}
		n.setType(lt);
		return true;
	}
	
	public static boolean inferType(CtAssignment<?, ?> ass) {
		return inferType(ass, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtVariableAccess<?> acc, boolean allowCast) {
		if (acc instanceof CtFieldAccess) {
			CtFieldAccess fa = (CtFieldAccess) acc;
			boolean isLength = acc.getVariable().toString().equals("length");
			if (isLength) {
				if (fa.getTarget() == null) return false;
				if (!(fa.getTarget().getType() instanceof CtArrayTypeReference))
					return false;
				fa.setType(acc.getFactory().Type().INTEGER_PRIMITIVE);
				return true;
			}
			// For ".class" the target might be null
			// XXX: how about this access?
			if (fa.getTarget() != null) {
				CtExpression tEle = fa.getTarget();
				// XXX: This shit is used with CtCodeSnippet
				if (!(tEle instanceof CtCodeSnippetExpression)) {
					if (tEle.getType().isPrimitive() || tEle.getType().equals(tEle.getFactory().Type().nullType()))
						return false;
					if (!typeCompatible(fa.getVariable().getDeclaringType(), tEle.getType())) {
						if (allowCast && typeCompatible(tEle.getType(), fa.getVariable().getDeclaringType())) {
							List<CtTypeReference> newc = new ArrayList<CtTypeReference>();
							newc.add(fa.getVariable().getDeclaringType());
							tEle.setTypeCasts(newc);
						}
						else
							return false;
					}
				}
			}
		}
		CtVariableAccess n = acc;
		if (n.getVariable() == null) {
			// super may cause this
			return n.getType() != null;
		} else {
			n.setType(n.getVariable().getType());
		}
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtUnaryOperator<?> uop) {
		CtUnaryOperator n = uop;
		CtTypeReference<?> tref = n.getOperand().getType();
		if (uop.getKind() == UnaryOperatorKind.NOT) {
			if (!tref.getSimpleName().contains("boolean") && !tref.getSimpleName().contains("Boolean")) return false;
		}
		n.setType(tref);
		return true;
	}

	@SuppressWarnings("rawtypes")
	private static boolean checkParameterType(List<CtTypeReference<?>> tparas, List<CtExpression<?>> args, boolean allowCast) {
		CtTypeReference<?> varargTref = null;
		if (tparas.size() > 0) {
			CtTypeReference lastArg = tparas.get(tparas.size() - 1);
			if (lastArg instanceof CtArrayTypeReference) {
				varargTref = ((CtArrayTypeReference) lastArg).getComponentType();
			}
		}
		// Next we are going to check the argument type and the supplied
		// actual argument actually matches
		if (varargTref == null) {
			if (tparas.size() != args.size())
				return false;
		}
		else {
			if (tparas.size() > args.size() + 1)
				return false;
		}
		for (int i = 0; i < args.size(); i++) {
			boolean compatible = false;
			// This may happen when a null value is matched in the pre-tree.
			// and then transformed to here. This is a malformed call, and we should
			// reject!
			if (args.get(i) == null) {
				return false; 
			}
			// XXX: I will just let this pass, I don't want to deal with NewAnonymousClass shit
			if (args.get(i) instanceof CtNewClass) {
				compatible = true;
			}
			Factory f = args.get(i).getFactory();
			if (args.get(i).getType().equals(f.Type().VOID_PRIMITIVE) || args.get(i).getType().equals(f.Type().VOID))
				return false;
			// XXX: we are going to be slightly more permissive for varargs
			// Mostly because spoon does not have good support for it
			if (i < tparas.size()) {
				if (typeCompatible(tparas.get(i), args.get(i).getType())) {
					compatible = true;
				}
			}
			if (!compatible)
				if (i >= tparas.size() - 1)
					if (varargTref != null)
						if (typeCompatible(varargTref, args.get(i).getType()))
							compatible = true;
			if (!compatible) {
				if (allowCast && i < tparas.size() && typeCompatible(args.get(i).getType(), tparas.get(i))) {
					List<CtTypeReference<?>> newc = new ArrayList<CtTypeReference<?>>();
					newc.add(tparas.get(i));
					args.get(i).setTypeCasts(newc);
				}
				else {
					return false;
				}
			}
			// It is fine to not add this cast, I just add it to make the test case check easier
			/*if (i < tparas.size() && compatible) {
				Factory f = args.get(i).getFactory();
				if (args.get(i).getType().equals(f.Type().nullType())) {
					ArrayList<CtTypeReference<?>> casts = new ArrayList<CtTypeReference<?>>();
					casts.add(tparas.get(i));
					args.get(i).setTypeCasts(casts);
				}
			}*/
		}		
		return true;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtInvocation<?> inv, boolean allowCast) {
		CtInvocation n = inv;
		// First we are going to check the target type and the called
		// method actually match
		CtExecutableReference<?> eref = n.getExecutable();
		// XXX: Somehow this might become null
		// but maybe I need to do more for this access checking
		if (n.getTarget() != null) {
			CtTypeReference<?> tt = n.getTarget().getType();
			// Primitive does not work here
			if (tt.isPrimitive() || tt.equals(n.getFactory().Type().nullType()))
				return false;
			// XXX: We may be able to do more than this,
			// but somehow I think the current checking probably enough
			if (!typeCompatible(eref.getDeclaringType(), tt))
				if (allowCast && typeCompatible(tt, eref.getDeclaringType())) {
					List<CtTypeReference> newc = new ArrayList<CtTypeReference>();
					newc.add(eref.getDeclaringType());
					n.getTarget().setTypeCasts(newc);
				}
				else
					return false;
		}
		
		List<CtTypeReference<?>> tparas = eref.getParameters();
		List<CtExpression<?>> args = n.getArguments();
		if (!checkParameterType(tparas, args, allowCast))
			return false;
		n.setType(eref.getType());
		return true;
	}
	
	public static boolean inferType(CtInvocation<?> inv) {
		return inferType(inv, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtNewClass<?> newclass, boolean allowCast) {
		CtNewClass n = newclass;
		CtExecutableReference<?> eref = n.getExecutable();
		// Next we are going to check the argument type and the supplied
		// actual argument actually matches
		if (eref.getParameters().size() != n.getArguments().size())
			return false;
		List<CtTypeReference<?>> tparas = eref.getParameters();
		List<CtExpression<?>> args = n.getArguments();
		if (!checkParameterType(tparas, args, allowCast))
			return false;
		n.setType(eref.getDeclaringType());
		return true;
	}
	
	public static boolean inferType(CtNewClass<?> newclass) {
		return inferType(newclass, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean inferType(CtConstructorCall<?> call, boolean allowCast) {
		CtConstructorCall n = call;
		CtExecutableReference<?> eref = n.getExecutable();
		n.setType(eref.getDeclaringType());
		// Next we are going to check the argument type and the supplied
		// actual argument actually matches
		if (eref.getParameters().size() != n.getArguments().size())
			return false;
		List<CtTypeReference<?>> tparas = eref.getParameters();
		List<CtExpression<?>> args = n.getArguments();
		if (!checkParameterType(tparas, args, allowCast))
			return false;
		return true;
	}
	
	public static boolean inferType(CtConstructorCall<?> call) {
		return inferType(call, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean inferTypeArrayAcc(CtArrayAccess n) {
		if (!(n.getTarget().getType() instanceof CtArrayTypeReference)) return false;
		if (!isIntegerType(n.getIndexExpression().getType())) return false;
		CtArrayTypeReference arrtype = (CtArrayTypeReference) n.getTarget().getType();
		n.setType(arrtype.getComponentType());
		return true;
	}

	@SuppressWarnings("rawtypes")
	public static boolean inferType(CtArrayWrite<?> accw) {
		CtArrayAccess n = accw;
		return inferTypeArrayAcc(n);
	}

	@SuppressWarnings("rawtypes")
	public static boolean inferType(CtArrayRead<?> accr) {
		CtArrayAccess n = accr;
		return inferTypeArrayAcc(n);
	}

	public static boolean typeEquals(CtTypeReference<?> ctype, String str) {
		return ctype.getSimpleName().equals(str);
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean typeCompatible(CtTypeReference<?> a, CtTypeReference<?> b) {
		String astr = a.toString();
		String bstr = b.toString();
		if (astr.equals(bstr))
			return true;
		// Null can assign to anything that is not primitive
		if (!a.isPrimitive() && b.equals(b.getFactory().Type().nullType()))
			return true;
		// Object can be compatible with anything
		if (a.toString().equals("java.lang.Object"))
			return true;
		// XXX: We do not handle type parameters right now, we assume it all pass
		if (a instanceof CtTypeParameterReference)
			return true;
		// Allow auto-boxing
		if (b.isPrimitive())
			if (typeCompatible(a, b.box())) {
				return true;
			}
		// Array does not match non-array
		if ((a instanceof CtArrayTypeReference) != (b instanceof CtArrayTypeReference))
			return false;
		// XXX: For the shit of type parameters, I am going to just treat it as 
		// Object and allow it to pass always
		if (!a.isPrimitive())
			if (a.getPackage() == null && astr.contains("?") /* not sure whether this will cause problem */) {
				return true;
			}
		if (a.isPrimitive() && b.isPrimitive()) {
			// I hate spoon, I have no idea why spoon does not have this rule
			if (typeEquals(b, "char") && (typeEquals(a, "int") || typeEquals(a, "long")))
				return true;
		}
		if ((a instanceof CtArrayTypeReference) && (b instanceof CtArrayTypeReference)) {
			return typeCompatible(((CtArrayTypeReference)a).getComponentType(), ((CtArrayTypeReference)b).getComponentType());
		}
		String qstr = astr + "-X-" + bstr;
		if (!compCache.containsKey(qstr)) {
			boolean ret = false;
			boolean failed = false;
			Level origLevel = Launcher.LOGGER.getLevel();
			// XXX: This is to silence the stupid error message inside spoon,
			// This is very hacky, I hate it
			Launcher.LOGGER.setLevel(Level.FATAL);
			try {
				//ret = (b != null && isSubtypeOfCache(b, a));
				ret = a.isAssignableFrom(b);
			}
			catch (SpoonClassNotFoundException e) {
				// so this is a external class. What we can do there is just to compare their strings.
				// This may be inaccurate but whatever
				failed = true;
			}
			Launcher.LOGGER.setLevel(origLevel);
			
			// Search over the binding info, this is our last hope
			if (failed && !a.isPrimitive() && !b.isPrimitive()) {
				CtTypeBinding abind = StaticAnalyzer.findTypeBinding(a.getFactory(), a, true);
				CtTypeBinding bbind = StaticAnalyzer.findTypeBinding(b.getFactory(), b, true);
				if (abind != null && bbind != null) {
					CtTypeBinding cur = bbind;
					String aname = abind.getFullName();
					while (cur != null && !aname.equals(cur.getFullName())) {
						for (CtTypeBinding intf : cur.getSuperInterfaces()) {
							if (interfaceCompImpl(aname, intf)) {
								ret = true;
								break;
							}
						}
						if (ret) break;
						cur = cur.getSuperType();
					}
					ret = (cur != null);
				}
			}
			
			compCache.put(qstr, ret);
			//cnt2 ++;
		}
		/*cnt1 ++;
		if (cnt1 % 10000 == 0)
			System.out.println("TypeComp cache miss " + cnt2 + ", tot " + cnt1);*/
		return compCache.get(qstr);
	}

	private static boolean interfaceCompImpl(String aname, CtTypeBinding cur) {
		if (aname.equals(cur.getFullName()))
			return true;
		for (CtTypeBinding intf : cur.getSuperInterfaces())
			if (interfaceCompImpl(aname, intf))
				return true;
		return false;
	}

	@SuppressWarnings("rawtypes")
	public static CtTypeReference<?> getExprType(MyCtNode n) {
		Object o = n.getRawObject();
		if (!(o instanceof CtExpression))
			return null;
		CtExpression exp = (CtExpression) o;
		return exp.getType();
	}

	public static boolean isType(MyCtNode n, String str) {
		CtTypeReference<?> t = getExprType(n);
		if (t == null) return false;
		return typeEquals(t, str);
	}

	public static boolean isArrayType(MyCtNode n) {
		CtTypeReference<?> t = getExprType(n);
		if (t == null)
			return false;
		return (t instanceof CtArrayTypeReference);
	}

	public static boolean isIntegerType(CtTypeReference<?> t) {
		String name = t.getSimpleName();
		return name.equals("int") || name.equals("long") || name.equals("short");
	}

	public static boolean isIntegerType(MyCtNode n) {
		CtTypeReference<?> t = getExprType(n);
		if (t == null)
			return false;
		return isIntegerType(t);
	}

	public static boolean isCompatibleType(CtTypeReference<?> ta, MyCtNode n) {
		CtTypeReference<?> tb = getExprType(n);
		if (tb == null)
			return false;
		return typeCompatible(ta, tb);
	}

	public static boolean isCompatibleType(MyCtNode n, MyCtNode n2) {
		CtTypeReference<?> ta = getExprType(n);
		CtTypeReference<?> tb = getExprType(n2);
		if ((ta == null) || (tb == null))
			return false;
		return typeCompatible(ta, tb);
	}
}
