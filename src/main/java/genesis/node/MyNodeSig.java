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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.support.reflect.code.CtBinaryOperatorImpl;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtStatementImpl;
import spoon.support.reflect.declaration.CtElementImpl;

final public class MyNodeSig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final Class<?> sig;
	private final boolean iscol;
	private final BinaryOperatorKind binop;
	private final UnaryOperatorKind uop;
	
	protected MyNodeSig() {
		sig = null;
		iscol = false;
		binop = null;
		uop = null;
	}
	
	// XXX: In the original spoon tree, the StatementImpl abstract class
	// is really bad in the hiarachy and does not cover all statements. 
	// It is better to always use its parent CodeElementImpl instead.
	private Class<?> normalize(Class<?> c) {
		if (c == null) return null;
		if (c.equals(CtStatementImpl.class))
			return c.getSuperclass();
		else
			return c;
	}
	
	public MyNodeSig(Class<?> sig, boolean iscol) {
		this.iscol = iscol;
		this.sig = normalize(sig);
		this.binop = null;
		this.uop = null;
	}

	public MyNodeSig(Object ele, boolean iscol) {
		this.iscol = iscol;
		if (ele == null) {
			this.sig = null;
			this.binop = null;
			this.uop = null;
		}
		else {
			this.sig = normalize(ele.getClass());
			if (ele instanceof BinaryOperatorKind) {
				this.binop = (BinaryOperatorKind) ele;
			}
			else
				this.binop = null;
			if (ele instanceof UnaryOperatorKind) 
				this.uop = (UnaryOperatorKind) ele;
			else
				this.uop = null;
		}
	}

	public MyNodeSig getCommonSuperSig(MyNodeSig a) {
		if (iscol != a.iscol)
			return new MyNodeSig(Object.class, false);
		else {
			if (sig == null)
				return a;
			if (a.sig == null)
				return this;
			if (equals(a))
				return this;
			Class<?> tmp = sig;
			while (!tmp.isAssignableFrom(a.sig)) {
				tmp = tmp.getSuperclass();
			}
			return new MyNodeSig(tmp, iscol);
		}
	}

	public MyNodeSig getSuperSig() {
		if (sig == null) return null;
		if (binop != null || uop != null)
			return new MyNodeSig(sig, iscol);
		if ((sig == Object.class) && (iscol == false))
			return null;
		else if (sig == Object.class) {
			return new MyNodeSig(Object.class, false);
		}
		else 
			return new MyNodeSig(sig.getSuperclass(), iscol);
	}

	@Override 
	public int hashCode() {
		int h = iscol ? 0 : 1;
		if (sig != null)
			h +=  sig.hashCode();
		if (binop != null)
			h = h ^ (binop.hashCode());
		if (uop != null)
			h = h ^ (uop.hashCode());
		return h;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (!( o instanceof MyNodeSig))
			return false;
		else {
			MyNodeSig a = (MyNodeSig) o;
			return (a.sig == sig) && (a.iscol == iscol) && (a.binop == binop) && (a.uop == uop);
		}
	}
	
	@Override
	public String toString() {
		if (binop != null)
			return "BOP:" + binop.toString();
		else if (uop != null)
			return "UOP:" + uop.toString();
		else {
			String str = "";
			if (iscol)
				str = "col:";
			if (sig != null) {
				String tmp = sig.toString();
				int index = tmp.indexOf(".Ct");
				if (index != -1)
					str += tmp.substring(index + 1);
				else
					str += tmp;
			}
			else
				str += "null";
			return str;
		}
	}

	public boolean isCodeElement() {
		if ((binop != null) || (uop != null)) return true;
		if (sig != null)
			return CtElementImpl.class.isAssignableFrom(sig);
		return false;
	}

	public boolean isClass(Class<?> clazz, boolean b) {
		if (sig == null)
			return false;
		else
			return clazz.isAssignableFrom(sig) && iscol == b;
	}

	public boolean isSuperOrEqual(MyNodeSig a) {
		if (a == null) return false;
		if (a.sig == null) {
			if (sig == Object.class)
				return true;
			else
				return iscol == a.iscol;
		}
		MyNodeSig tmp = a;
		while (tmp != null) {
			if (equals(tmp)) return true;
			tmp = tmp.getSuperSig();
		}
		return false;
	}
	
	public boolean isAssignableFrom(Class<?> clazz) {
		return !iscol && sig.isAssignableFrom(clazz);
	}		

	public Class<?> getClassSig() {
		return sig;
	}

	public boolean isCollection() {
		return iscol;
	}

	public MyNodeSig elementSig() {
		assert( iscol );
		return new MyNodeSig(sig, false);
	}

	public boolean isAssignableFrom(MyNodeSig a) {
		if (sig == null)
			return true;
		if (a.sig == null)
			return false;
		return iscol == a.iscol && (sig.isAssignableFrom(a.sig));
	}

	public boolean isBinop() {
		return binop != null;
	}		
	
	public BinaryOperatorKind getBinop() {
		return binop;
	}
	
	public boolean isUop() {
		return uop != null;
	}
	
	public UnaryOperatorKind getUop() {
		return uop;
	}
	
	public static Set<MyNodeSig> canonicalSigSet(Set<MyNodeSig> s) {
		Set<MyNodeSig> res = new HashSet<MyNodeSig>();
		for (MyNodeSig sig : s) {
			MyNodeSig tmp = sig;
			while (tmp != null) {
				res.add(tmp);
				tmp = tmp.getSuperSig();
			}
		}
		return res;
	}
}
