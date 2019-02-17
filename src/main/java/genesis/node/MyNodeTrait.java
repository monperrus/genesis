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
import java.util.ArrayList;
import java.util.Collection;

import genesis.GenesisException;
import genesis.utils.StringUtil;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public final class MyNodeTrait implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Object obj;
	private ArrayList<Object> objs; 
	private boolean isTypeRef;
	
	protected MyNodeTrait() {
		obj = null;
		objs = null;
		isTypeRef = false;
	}
	
	public MyNodeTrait(Object o) {
		if (o != null)
			assert(o instanceof Serializable);
		if (o instanceof CtTypeReference) {
			this.obj = ((CtTypeReference<?>) o).getQualifiedName();
			this.objs = null;
			isTypeRef = true;
		}
		else {
			this.obj = o;
			if (o instanceof Collection) {
				Collection<?> c = (Collection<?>) o;
				objs = new ArrayList<Object>(c);
			}
			else 
				objs = null;
			isTypeRef = false;
		}
	}
	
	@Override
	public int hashCode() {
		if (obj == null)
			return 0;
		if (objs == null)
			return obj.hashCode();
		else {
			return objs.hashCode();
		}
	}
	
	@Override
	public boolean equals(Object a) {
		if (!(a instanceof MyNodeTrait))
			return false;
		MyNodeTrait b = (MyNodeTrait) a;
		if (isTypeRef != b.isTypeRef)
			return false;
		// So far I am only going to call equals
		if ((objs == null) != (b.objs == null))
			return false;
		if (objs == null) {
			if (obj == null)
				return b.obj == null;
			else
				return obj.equals(b.obj);
		}
		else {
			return objs.equals(b.objs);
			/*if (objs.size() != b.objs.size()) return false;
			boolean ret = true;
			for (int i = 0; i < objs.size(); i++)
				if (!objs.get(i).equals(b.objs.get(i))) {
					ret = false;
					break;
				}
			return ret;*/
		}
	}
	
	@Override
	public String toString() {
		if (objs == null) {
			if (obj == null)
				return "null";
			else
				return obj.toString();
		}
		else
			return objs.toString();
	}

	public MyCtNode convertToNodeWithClone(Factory f) {
		if (isTypeRef) {
			String qName = (String) obj;
			return new MyCtNode(f.Type().createReference(qName), true);
		}
		else {
			// So far I am going to assume either the obj will be Immutable or Collections,
			// which might be problematic
			if (objs != null) {
				try {
					return new MyCtNode(obj.getClass().getMethod("clone").invoke(obj), true);
				}
				catch (Exception e) {
					e.printStackTrace();
					throw new GenesisException("trait clone failed!");
				}
			}
			else
				return new MyCtNode(obj, true);
		}
	}

	// This might be called when collapsing text message
	public boolean isTextMessage() {
		if (obj == null) return false;
		if (!(obj instanceof String))
			return false;
		else
			return StringUtil.isTextMessage((String)obj);
	}
}
