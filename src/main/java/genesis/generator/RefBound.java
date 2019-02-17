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
import java.util.Arrays;

import genesis.GenesisException;

public class RefBound implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private static final int valLen = 4;

	int[] val;

	/*int inBefore;
	int inFunc;
	int inPackage;
	int inBinding;*/

	public RefBound() {
		val = new int[valLen];
		/*inBefore = 0;
		inFunc = 0;
		inPackage = 0;
		inBinding = 0;*/
	}

	public RefBound(int inBefore, int inFunc, int inFile, int inBinding) {
		this();
		this.val[0] = inBefore;
		this.val[1] = inFunc;
		this.val[2] = inFile;
		this.val[3] = inBinding;
		/*
		this.inBefore = inBefore;
		this.inFunc = inFunc;
		this.inPackage = inPackage;
		this.inBinding = inBinding;*/
	}

	public RefBound(RefBound a) {
		this();
		System.arraycopy(a.val, 0, val, 0, valLen);
		/*
		this.inBefore = a.inBefore;
		this.inFunc = a.inFunc;
		this.inPackage = a.inPackage;
		this.inBinding = a.inBinding;*/
	}

	public RefBound mergeWith(RefBound a) {
		RefBound ret = new RefBound();
		int sum1 = 0;
		int sum2 = 0;
		for (int i = valLen - 1; i >= 0; i--) {
			sum1 += val[i];
			sum2 += a.val[i];
			ret.val[i] = sum1 > sum2 ? sum1 : sum2;
		}
		for (int i = 0; i < valLen - 1; i++) {
			ret.val[i] -= ret.val[i+1];
		}
		return ret;
		/*ret.inBinding = inBinding > a.inBinding ? inBinding : a.inBinding;
		int tmp0 = inBinding + inPackage;
		int tmp1 = a.inBinding + a.inPackage;
		ret.inPackage = tmp0 > tmp1 ? tmp0 : tmp1;
		tmp0 = inBinding + inPackage + inFunc;
		tmp1 = a.inBinding + a.inPackage + a.inFunc;
		ret.inFunc = tmp0 > tmp1 ? tmp0 : tmp1;
		tmp0 = inBinding + inPackage + inFunc + inBefore;
		tmp1 = a.inBinding + a.inPackage + a.inFunc + a.inBefore;
		ret.inBefore = tmp0 > tmp1 ? tmp0 : tmp1;
		ret.inBefore -= ret.inFunc;
		ret.inFunc -= ret.inPackage;
		ret.inPackage -= ret.inBinding;
		return ret;*/
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("(");
		for (int i = 0; i < valLen; i++) {
			if (i != 0) ret.append(",");
			ret.append(Integer.toString(val[i]));
		}
		ret.append(")");
		return ret.toString();
		/*return "(" + Integer.toString(inBefore) + "," +
				Integer.toString(inFunc) + "," + Integer.toString(inPackage) + "," + Integer.toString(inBinding) + ")";*/
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(val);
		//return inBefore ^ (inFunc + 1) ^ (inPackage + 2) ^ (inBinding * 3);
	}

	public boolean greaterEq(RefBound a) {
		int sum1 = 0;
		int sum2 = 0;
		for (int i = valLen -1; i >= 0; i--) {
			sum1 += val[i];
			sum2 += a.val[i];
			if (sum1 < sum2) return false;
		}
		return true;
		/*return inBinding >= a.inBinding &&
				(inBinding + inPackage) >= (a.inPackage + a.inPackage) &&
				(inBinding + inPackage + inFunc) >= (a.inBinding + a.inPackage + a.inFunc) &&
				(inBinding + inPackage + inFunc + inBefore) >= (a.inBinding + a.inPackage + a.inFunc + a.inBefore);*/
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RefBound)) return false;
		RefBound a = (RefBound)o;
		return Arrays.equals(val, a.val);
		//return (inBinding == a.inBinding) && (inBefore == a.inBefore) && (inFunc == a.inFunc) && (inPackage == a.inPackage);
	}

	public void add(RefBound a) {
		for (int i = 0; i < valLen; i++)
			val[i] += a.val[i];
		/*
		inBinding += a.inBinding;
		inBefore += a.inBefore;
		inFunc += a.inFunc;
		inPackage += a.inPackage;*/
	}

	public boolean subs(RefBound a) {
		int left = 0;
		for (int i = 0; i < valLen; i++) {
			if (val[i] < a.val[i] + left) {
				left = a.val[i] + left - val[i];
				val[i] = 0;
			}
			else {
				val[i] -= a.val[i] + left;
				left = 0;
			}
		}
		return left == 0;
		/*
		if (inBefore < a.inBefore) {
			left = a.inBefore - inBefore;
			inBefore = 0;
		}
		else
			inBefore -= a.inBefore;
		if (inFunc < a.inFunc + left) {
			left = a.inFunc + left - inFunc;
			inFunc = 0;
		}
		else {
			inFunc -= a.inFunc + left;
			left = 0;
		}
		if (inPackage < a.inPackage + left) {
			left = a.inPackage + left - inPackage;
			inPackage = 0;
		}
		else {
			inPackage -= a.inPackage + left;
			left = 0;
		}
		assert( inBinding >= a.inBinding + left);
		inBinding -= a.inBinding + left;*/
	}

	public boolean isZero() {
		for (int i = 0; i < valLen; i++)
			if (val[i] != 0) return false;
		return true;
		//return inBinding == 0 && inPackage == 0 && inBefore == 0 && inFunc == 0;
	}

	public void decVal(int idx) {
		if (val[idx] > 0)
			val[idx] --;
		else {
			if (idx != valLen - 1)
				decVal(idx + 1);
			else
				throw new GenesisException("attempt to exceed the specified limit in ref bound when generating!");
		}
	}

	public void decInBefore() {
		decVal(0);
	}

	public void decInFunc() {
		decVal(1);
	}

	public void decInFile() {
		decVal(2);
	}

	public void decInBinding() {
		decVal(3);
	}

	public void incInBefore() {
		val[0] ++;
	}

	public void incInFunc() {
		val[1] ++;
	}

	public void incInFile() {
		val[2] ++;
	}

	public void incInBinding() {
		val[3] ++;
	}

	public int inBefore() {
		return val[0];
	}

	public int inFunc() {
		return val[1];
	}

	public int inFile() {
		return val[2];
	}

	public int inBinding() {
		return val[3];
	}

	public int sums() {
		int ret = 0;
		for (int i = 0; i < valLen; i++)
			ret += val[i];
		return ret;
	}
}