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
package genesis.learning;

import java.util.HashSet;

import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;

public class DecomposedCodePair {
	public HashSet<MyNodeSig> insides;
	public MyCtNode before, after;
	
	public DecomposedCodePair() {
		insides = null;
		before = null;
		after = null;
	}
	
	public DecomposedCodePair(HashSet<MyNodeSig> s, MyCtNode t1, MyCtNode t2) {
		insides = new HashSet<MyNodeSig>(s);
		before = t1;
		after = t2;
	}
	
	public String toString() {
		String ret = insides.toString() + "\n";
		ret += "Before tree: \n";
		ret += before.toString() + "\n";
		ret += "After tree: \n";
		ret += after.toString() + "\n";
		return ret;
	}
}