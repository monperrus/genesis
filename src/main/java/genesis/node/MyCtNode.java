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
package genesis.node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import genesis.Config;
import genesis.GenesisException;
import genesis.utils.StringUtil;
import spoon.processing.FactoryAccessor;
import spoon.reflect.binding.CtBinding;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.FactoryImpl;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.DefaultCoreFactory;
import spoon.support.StandardEnvironment;
import spoon.support.reflect.code.CtBinaryOperatorImpl;
import spoon.support.reflect.code.CtCatchImpl;
import spoon.support.reflect.code.CtCatchVariableImpl;
import spoon.support.reflect.code.CtCodeSnippetExpressionImpl;
import spoon.support.reflect.code.CtLiteralImpl;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.reflect.declaration.CtElementImpl;
import spoon.support.reflect.declaration.CtPackageImpl;
import spoon.support.util.RtHelper;

public final class MyCtNode {

	private final Object ele;
	private transient Object pele;
	private final boolean iscol;
	private final boolean istrait;

	private transient Lock childLock;
	private transient ArrayList<String> childNames;
	private transient ArrayList<Object> children;
	private transient ArrayList<MyCtNode> childrenCtNode;

	private static final HashSet<String> ignoredF;
	private static LoadingCache<Object, Field> parentCache;

	static {
		ignoredF = new HashSet<String>();
		ignoredF.add("parent");
		ignoredF.add("position");
		ignoredF.add("factory");
		ignoredF.add("docComment");
		// This is the special binding structure tied to CtPackage. We only use it for reference
		ignoredF.add("bindings");
		//XXX: This may cause problem if we modified the declare of annotated function, but whatever
		ignoredF.add("annotations");
		parentCache = CacheBuilder.newBuilder().concurrencyLevel(Config.concurrencyLevel).maximumSize(Config.cacheSize).weakKeys().build(
					new CacheLoader<Object, Field>() {
						public Field load(Object o) {
							Class<?> clazz = o.getClass();
							Field f = null;
							while (clazz != null && f == null) {
								try {
									f = clazz.getDeclaredField("parent");
								}
								catch (NoSuchFieldException e) {
									f = null;
									clazz = clazz.getSuperclass();
								}
							}
							if (f == null)
								throw new GenesisException("Exception when attempt to find parent of an ele!");
							return f;
						}
					}
				);
	}

	public MyCtNode(Object ele, boolean istrait) {
		this.ele = ele;
		this.istrait = istrait;
		if (Collection.class.isInstance(ele) && !istrait) {
			children = null;
			childrenCtNode = null;
			//Collection<?> col = (Collection<?>) ele;
			//children = new ArrayList<Object>(col);
			//childrenCtNode = new ArrayList<MyCtNode>(Collections.<MyCtNode>nCopies(children.size(), null));
			iscol = true;
		}
		else {
			iscol = false;
			children = null;
			childrenCtNode = null;
		}
		childNames = null;
		pele = null;
		childLock = new ReentrantLock();
	}

	public MyCtNode(Object ele, boolean istrait, Object pele) {
		this(ele, istrait);
		this.pele = pele;
	}

	public static boolean shouldIgnoreField(String name) {
		return ignoredF.contains(name);
	}

	public boolean isReference() {
		return !iscol && (ele instanceof CtReference);
	}

	public boolean nodeEquals(MyCtNode n) {
		// When checking equality, we skip signatures for col
		// We do want {ifstmt; assign} and {ifstmt;ifstmt} to match
		if (isCollection()) {
			if (n.isCollection())
				return getColSize() == n.getColSize();
			else
				return false;
		}
		if (!n.nodeSig().equals(nodeSig())) return false;
		if (isReference()) {
			// We are going to compare the raw simple name for the reference
			// Hope this is enough
		    CtReference ref1 = (CtReference) ele;
		    CtReference ref2 = (CtReference) n.ele;
		    if (ref1 instanceof CtFieldReference) {
		    	// For field reference, we need to do a little bit extra to
		    	// take its type into account!
		    	if (ref2 instanceof CtFieldReference)
		    		return ((CtFieldReference<?>) ref1).getQualifiedName().equals(((CtFieldReference<?>) ref2).getQualifiedName()) &&
		    				ref1.toString().equals(ref2.toString());
		    	else
		    		return false;
		    }
		    return ref1.toString().equals(ref2.toString());
		}
		else if (isTrait()) {
			return nodeTrait().equals(n.nodeTrait());
		}
		else {
			return true;
		}
	}

	public boolean treeEquals(MyCtNode a) {
		// We are going to try to recursively the call equals on subnodes, this will
		// be a little bit expensive
		if (!nodeEquals(a)) return false;
		int m1 = getNumChildren();
		int m2 = a.getNumChildren();
		if (m1 != m2) return false;
		for (int i = 0; i < m1; i++) {
			MyCtNode x1 = getChild(i);
			MyCtNode x2 = a.getChild(i);
			if ((isCollection() || (getRawObject() instanceof CtVariable) || !getChildName(i).equals("type")) && !x1.treeEquals(x2))
				return false;
		}
		return true;
	}

	public MyCtNode getChild(String name) {
		// This method is a little expensive
		if (isReference() || isTrait() || isCollection())
			throw new GenesisException("getChild(str) should only called for CtElement");
		if (children == null)
			initializeChildren();
		int n = children.size();
		for (int i = 0; i < n; i++)
			if (childNames.get(i).equals(name)) {
				if (childrenCtNode.get(i) == null)
					childrenCtNode.set(i, new MyCtNode(children.get(i), isTraitField(childNames.get(i), children.get(i))));
				return childrenCtNode.get(i);
			}
		return null;
	}

	public boolean isCollection() {
		return iscol;
	}

	public int getNumChildren() {
		if (isReference() || isTrait())
			return 0;
		if (children == null)
			initializeChildren();
		return children.size();
	}

	public MyCtNode getChild(int i) {
		if (isReference() || isTrait())
			throw new GenesisException("getChild() should never be called for references!");
		if (children == null)
			initializeChildren();
		if (!iscol) {
			if (childrenCtNode.get(i) == null)
				childrenCtNode.set(i, new MyCtNode(children.get(i), isTraitField(childNames.get(i), children.get(i)), ele));
			return childrenCtNode.get(i);
		}
		else {
			if (childrenCtNode.get(i) == null)
				childrenCtNode.set(i, new MyCtNode(children.get(i), false));
			return childrenCtNode.get(i);
		}
	}

	private static boolean isTraitField(String string, Object object) {
		// A corner case where TypeReference as value field of CtLiteral
		if ((object instanceof CtTypeReference) && (string.equals("value")))
			return true;
		if ((object instanceof CtReference) || (object instanceof CtElement))
			return false;
		// XXX: This is to a hack to fix some special tags like [public]
		if ((object instanceof Collection) && (!string.equals("modifiers"))) {
			return false;
		}
		return true;
	}

	public String getChildName(int i) {
		if (iscol)
			throw new GenesisException("MyCtNode.getChildName() should never be called for collection!");
		if (childNames == null)
			initializeChildren();
		return childNames.get(i);
	}

	private void initializeChildren() {
		childLock.lock();
		try {
			if (children != null) {
				// This may happen if two threads try to initialize at the same time
				return;
			}
			if (iscol) {
				children = new ArrayList<Object>((Collection<?>)ele);
				childNames = null;
				childrenCtNode = new ArrayList<MyCtNode>(Collections.<MyCtNode>nCopies(children.size(), null));
			}
			else {
				childNames = new ArrayList<String>();
				children = new ArrayList<Object>();
				try {
					for (Field f : RtHelper.getAllFields(ele.getClass())) {
						if (shouldIgnoreField(f.getName())) continue;
						if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
						f.setAccessible(true);
						Object o = f.get(ele);
						if (o instanceof Map)
							throw new GenesisException("There are maps in the model!");
						childNames.add(f.getName());
						children.add(o);
						/*
						if (Collection.class.isInstance(o)) {
							Collection<?> c = (Collection<?>)o;
							// Don't modify it if we don't have to
							if (c.size() == 0)
								colParent.put(System.identityHashCode(o), ele);
						}*/
					}
				}
				catch (IllegalAccessException e) {
					throw new GenesisException("getChildName() error: " + e.getMessage(), e);
				}
				childrenCtNode = new ArrayList<MyCtNode>(Collections.<MyCtNode>nCopies(children.size(), null));
			}
		}
		finally {
			childLock.unlock();
		}
	}

	public MyNodeSig nodeSig() {
		if (iscol) {
			if (children == null)
				initializeChildren();
			// XXX: This is a corner case, I am not sure what is the best way to handle it
			if (children.size() == 0)
				return new MyNodeSig(null, true);
			MyNodeSig tmp = new MyNodeSig(children.get(0), true);
			for (int i = 1; i < children.size(); i++) {
				tmp = tmp.getCommonSuperSig(new MyNodeSig(children.get(i), true));
			}
			return tmp;
		}
		else {
			return new MyNodeSig(ele, false);
		}
	}

	public HashSet<MyNodeSig> nodeSigSet() {
		HashSet<MyNodeSig> res = new HashSet<MyNodeSig>();
		res.add(nodeSig());
		int n = getNumChildren();
		for (int i = 0; i < n; i++) {
			MyCtNode x = getChild(i);
			res.addAll(x.nodeSigSet());
		}
		return res;
	}

	public boolean isTrait() {
		return istrait;
	}

	public MyNodeTrait nodeTrait() {
		return new MyNodeTrait(ele);
	}

	@Override
	public String toString() {
		if (ele == null)
			return "null";
		else
			return ele.toString();
	}

	public String codeString() {
		// XXX: A very primitive implementation
		if (iscol) {
			if (children == null)
				initializeChildren();
			boolean isArgumentList = false;
			if (children.size() != 0) {
				MyCtNode parent = parentEleNode();
				if (parent != null)
					if (parent.isEleClass(CtAbstractInvocation.class))
						isArgumentList = true;
			}
			StringBuffer ret = new StringBuffer();
			boolean first = true;
			for (Object o : children) {
				if (isArgumentList && !first)
					ret.append(", ");
				first = false;
				ret.append(o.toString().trim());
				char lastChar = ret.charAt(ret.length() - 1);
				if (!isArgumentList) {
					if (lastChar != '\n' && lastChar != ';' && lastChar != '}')
						ret.append(";\n");
					else if (lastChar == ';' || lastChar == '}')
						ret.append("\n");
				}
			}
			return ret.toString();
		}
		else
			return toString();
	}

	public void setParent(CtElement p) {
		if (iscol) {
			for (Object o : children) {
				if (o instanceof CtElement)
					((CtElement) o).setParent(p);
			}
		}
		else if (ele instanceof CtElement)
			((CtElement) ele).setParent(p);
	}
	
	public String codeString(MyCtNode replaced) {
		// XXX: I simply hate this hack
		if (replaced.isTrait()) 
			return this.toString();
		
		MyCtNode parentNode = replaced.parentEleNode();
		CtElement parentEle = (CtElement)parentNode.ele;
		CtElement orgiP = null;
		if (iscol) {
			if (children == null)
				initializeChildren();
			for (Object o : children) {
				if (o instanceof CtElement) {
					if (orgiP == null) {
						try {
							orgiP = ((CtElement) o).getParent();
						}
						catch (Exception ignore) {
							// We simply give up if we did not get its parent
						}
					}
					((CtElement) o).setParent(parentEle);
				}
			}
		}
		else if (ele instanceof CtElement) {
			try {
				orgiP = ((CtElement) ele).getParent();
			}
			catch (Exception ignore) {
				// We simply give up if we did not get its parent
			}
			((CtElement) ele).setParent(parentEle);
		}
		String ret = codeString();
		setParent(orgiP);
		
		if (iscol) {
			for (Object o : children) {
				if (o instanceof CtElement)
					((CtElement) o).setParent(orgiP);
			}
		}
		else if (ele instanceof CtElement)
			((CtElement) ele).setParent(orgiP);

		return ret;

	}

	public Object getRawObject() {
		return ele;
	}

	@SuppressWarnings("unchecked")
	public MyCtNode deepClone() {
		if (isCollection()) {
			try {
				Constructor<?> c = ele.getClass().getDeclaredConstructor();
				c.setAccessible(true);
				Collection<Object> r = (Collection<Object>) c.newInstance();
				int n = getNumChildren();
				for (int i = 0; i < n; i++) {
					MyCtNode tmp = getChild(i).deepClone();
					r.add(tmp.getRawObject());
				}
				return new MyCtNode(r, false);
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new GenesisException("Unable to clone a MyCtNode " + toString());
			}
		}
		else if (isTrait()) {
			if (ele instanceof FactoryAccessor)
				return nodeTrait().convertToNodeWithClone(((FactoryAccessor) ele).getFactory());
			else
				return nodeTrait().convertToNodeWithClone(null);
		}
		else if (ele instanceof FactoryAccessor) {
			FactoryAccessor a = (FactoryAccessor) ele;
			Factory spoonf = a.getFactory();
			return new MyCtNode(spoonf.Core().clone(a), false);
		}
		else
			throw new GenesisException("Unable to clone a MyCtNode " + toString());
	}

	public void acceptScanner(CtScanner s) {
		if (isTrait())
			throw new GenesisException("Attempt to scan a trait!");
		if (isCollection()) {
			if (children == null)
				initializeChildren();
			s.scan(children);
		}
		else
			s.scan(ele);
	}

	@Override
	public int hashCode() {
		if (ele == null)
			return 0;
		else
			return ele.hashCode();
	}

	@Override
	public boolean equals(Object a) {
		if (!(a instanceof MyCtNode))
			return false;
		return ele == ((MyCtNode)a).ele;
	}

	// XXX: We are going to assume the parent will not change,
	// and we are going to have some cache for this because it
	// is damn slow!
	private Field findParent(Object o) {
		try {
			return parentCache.get(o);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new GenesisException("Unable to get parent for " + o);
		}
		/* if (!parentCache.containsKey(o)) {
			Class<?> clazz = o.getClass();
			Field f = null;
			while (clazz != null && f == null) {
				try {
					f = clazz.getDeclaredField("parent");
				}
				catch (NoSuchFieldException e) {
					f = null;
					clazz = clazz.getSuperclass();
				}
			}
			if (f == null)
				throw new GenesisException("Exception when attempt to find parent of an ele!");
			parentCache.put(o, f);
		}
		return parentCache.get(o);*/
	}

	/*** Note that this function skips the collection CtNode and find the parent
	 * element node for the current MyCtNode. Inside, it uses the spoon parent field
	 * to get this done.
	 * @return a MyCtNode represents parent CtElement
	 */
	public MyCtNode parentEleNode() {
		if (pele == null) {
			Object o = null;
			if (isTrait() || isReference()) {
				throw new GenesisException("Cannot call parentEleNode() on a trait or a reference!");
			}
			else if (isCollection() && isColClass(CtReference.class)) {
				throw new GenesisException("Cannot call parentEleNode() on a collection of references!");
			}
			else if (isCollection()) {
				if (children == null)
					initializeChildren();
				if (children.size() == 0) {
					throw new GenesisException("Cannot call parentEleNode() on an empty collection without pele setup!");
				}
				o = children.get(0);
			}
			else
				o = ele;
			try {
				/*Class<?> clazz = o.getClass();
				Field f = null;
				while (clazz != null && f == null) {
					try {
						f = clazz.getDeclaredField("parent");
					}
					catch (NoSuchFieldException e) {
						f = null;
						clazz = clazz.getSuperclass();
					}
				}
				if (f == null)
					throw new GenesisException("Exception when attempt to find parent of an ele!");*/
				Field f = findParent(o);
				f.setAccessible(true);
				Object parent = f.get(o);
				if (parent == null)
					pele = null;
				else
					pele = parent; 
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new GenesisException("Exception when attempt to find parent of an ele!");
			}
		}
		if (pele == null)
			return null;
		else
			return new MyCtNode(pele, false);
	}

	public boolean isEleClass(Class<?> clazz) {
		return nodeSig().isClass(clazz, false);
	}

	public boolean isColClass(Class<?> clazz) {
		return nodeSig().isClass(clazz, true);
	}

	@SuppressWarnings("unchecked")
	public void collectionAdd(MyCtNode n) {
		assert( isCollection() );
		Collection<Object> col = (Collection<Object>) ele;
		col.add(n.ele);
		if (children != null) {
			children.add(n.ele);
			childrenCtNode.add(null);
		}
	}

	private void attachToFactory(final Factory f) {
		MyCtNode cur = this;
		MyCtNode lastPackage = null;
		while (true) {
			if (cur.isEleClass(CtPackageImpl.class))
				lastPackage = cur;
			MyCtNode tmp = cur.parentEleNode();
			if (tmp == null) break;
			cur = tmp;
		}
		CtElementImpl dummyone = (CtElementImpl) cur.ele;
		dummyone.setFactory(f);
		CtScanner factoryFix = new CtScanner() {
			private void recursiveApply(Object obj) {
				try {
					for (Field f : RtHelper.getAllFields(obj.getClass())) {
						if (shouldIgnoreField(f.getName())) continue;
						if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
						f.setAccessible(true);
						Object o = f.get(obj);
						if (o instanceof Collection || o instanceof CtReference)
							scan(o);
						// Not sure why the scanner does not visit this snippet code
						if (o instanceof CtCodeSnippetExpressionImpl)
							enter((CtElement) o);
						/*if (o instanceof Map)
							throw new GenesisException("There are maps in the model!");
						childNames.add(f.getName());
						children.add(o);
						colParent.put(System.identityHashCode(o), ele);*/
					}
				}
				catch (IllegalAccessException e) {
					throw new GenesisException("acceptScanner error: " + e.getMessage(), e);
				}
			}
			
			@Override
			public void enter(CtElement ele) {
				if (ele.getFactory() == null) {
					ele.setFactory(f);
					recursiveApply(ele);
				}
				/*
				if (ele instanceof CtTypedElement) {
					CtTypeReference<?> tref = ((CtTypedElement<?>) ele).getType();
					if (tref != null)
						enterReference(tref);
				}
				if (ele instanceof CtMultiTypedElement) {
					for (CtTypeReference<?> ref : ((CtMultiTypedElement) ele).getMultiTypes())
						enterReference(ref);
				}*/
			}

			@Override
			public void enterBinding(CtBinding b) {
				b.setFactory(f);
			}

			@Override
			public void enterReference(CtReference ref) {
				if (ref.getFactory() == null) {
					ref.setFactory(f);
					recursiveApply(ref);
				}
			}
		};
		factoryFix.initializeBindingVisit();
		lastPackage.acceptScanner(factoryFix);

		// Sometimes spoon will go back to the root package to resolve type, we have to make it right
		CtPackage rootPkg = f.Package().getRootPackage();
		assert(lastPackage.isEleClass(CtPackage.class));
		CtPackage newRootPkg = (CtPackage) lastPackage.ele;
		rootPkg.setPackages(newRootPkg.getPackages());
	}

	public void writeToStream(ObjectOutputStream os) throws IOException {
		os.writeObject(istrait);
		os.writeObject(ele);
	}

	public static MyCtNode readFromStream(ObjectInputStream is) throws IOException, ClassNotFoundException {
		// Going to start a new factory every time it reads from the file
		// The factory is somehow tied to the packages in the AST tree,
		// So different trees should not share the same factory whenever possible.
		Factory f = new FactoryImpl(new DefaultCoreFactory(),
				new StandardEnvironment());

		Boolean ist = (Boolean) is.readObject();
		Object o = is.readObject();
		MyCtNode ret = new MyCtNode(o, ist);
		if (!ist) {
			ret.attachToFactory(f);
		}
		return ret;
	}

	public int getColSize() {
		Collection<?> col = (Collection<?>) ele;
		return col.size();
	}

	public void invalidateChildrenCache() {
		children = null;
		childNames = null;
		childrenCtNode = null;
	}

	public boolean isNull() {
		return ele == null;
	}

	public Factory getFactory() {
		if (iscol) {
			if (children == null)
				initializeChildren();
			for (Object o : children) {
				if (o instanceof FactoryAccessor)
					return ((FactoryAccessor) o).getFactory();
			}
			return null;
		}
		else if (ele instanceof FactoryAccessor) {
			return ((FactoryAccessor) ele).getFactory();
		}
		else
			return null;
	}
	
	private static String getTextMessage(CtElement ele) {
		if (ele instanceof CtBinaryOperatorImpl) {
			CtBinaryOperatorImpl<?> bop = (CtBinaryOperatorImpl<?>) ele;
			BinaryOperatorKind k = bop.getKind();
			if (k != BinaryOperatorKind.PLUS)
				return null;
			String a = getTextMessage(bop.getLeftHandOperand()); 
			String b = getTextMessage(bop.getRightHandOperand());
			if (a == null || b == null)
				return null;
			return a + b;
		}
		else if (ele instanceof CtLiteralImpl) {
			CtLiteralImpl<?> literal = (CtLiteralImpl<?>) ele;
			Object v = literal.getValue();
			if (!(v instanceof String)) return null; // non string literal
			return (String) v;
		}
		else
			return "";
	}
	
	public static boolean isTextMessage(CtElement ele) {
		if (!(ele instanceof CtBinaryOperator) && !(ele instanceof CtLiteral))
			return false;
		String resStr = getTextMessage(ele);
		if (resStr == null) return false;
		return StringUtil.isTextMessage(resStr);
	}
	
	// This function is used to collapse text messages
	public boolean isTextMessageNode() {
		if (iscol) return false;
		if (istrait) {
			return nodeTrait().isTextMessage();
		}
		return isTextMessage((CtElement) ele);
	}

	public boolean isExpressionFieldOnly() {
		Class<?> tsig = getTypeSigInParentNode();
		if (CtExpression.class.isAssignableFrom((Class<?>) tsig) &&
			!CtStatement.class.isAssignableFrom((Class<?>) tsig))
			return true;
		return false;
	}

	public boolean isStatementFieldOnly() {
		Class<?> tsig = getTypeSigInParentNode();
		if (!CtExpression.class.isAssignableFrom((Class<?>) tsig) &&
			CtStatement.class.isAssignableFrom((Class<?>) tsig))
			return true;
		return false;
	}
	
	public Class<?> getTypeSigInParentNode() {
		// Just return its vanilla sig
		if (isTrait() || isReference())
			return ele.getClass();
		// XXX: Collection might be a snippet which is not reliable to use
		// I am going to use its children instead
		if (isCollection()) {
			if (getNumChildren() == 0)
				throw new GenesisException("Cannot call getTypeSigInParentNode() on an empty collection!");
			else
				return getChild(0).getTypeSigInParentNode();
		}
		parentEleNode();
		if (pele == null)
			throw new GenesisException("Unable to find parent for getTypeSigInParentNode()");
		Field targetf = null;
		boolean isCol = false;
		try {
			for (Field f : RtHelper.getAllFields(pele.getClass())) {
				if (shouldIgnoreField(f.getName())) continue;
				if (Modifier.isFinal(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
				f.setAccessible(true);
				Object o = f.get(pele);
				if (o == ele) {
					targetf = f;
					break;
				}
				if (o instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<Object> col = (Collection<Object>) o;
					for (Object obj: col)
						if (obj == ele) {
							targetf = f;
							isCol = true;
							break;
						}
				}
			}
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
			throw new GenesisException("Error when looking up reflection in getTypeSigInParentNode()!");
		}
		
		if (targetf == null)
			throw new GenesisException("Unable to find the field in its parent for getTypeSigInParentNode()!");
		
		// XXX: This is a very hacky if statement to get around
		// using genericType for collections. I don't want to use
		// getGenericType because it is not guranteed to be available
		// during runtime
		if (isCol) {
			if (CtStatementList.class.isAssignableFrom(pele.getClass()))
				return CtStatement.class;
			else if (CtSwitch.class.isAssignableFrom(pele.getClass()))
				return targetf.getType();
			else
				return CtExpression.class;
		}
		else
			return targetf.getType();
	}

	public boolean isLocalVarNameNode() {
		if (iscol) return false;
		if (istrait) {
			MyCtNode p = parentEleNode();
			return (p.isEleClass(CtLocalVariableImpl.class) || p.isEleClass(CtCatchVariableImpl.class)) && p.getChild("name").ele == ele;
		}
		else
			return false;
	}
}