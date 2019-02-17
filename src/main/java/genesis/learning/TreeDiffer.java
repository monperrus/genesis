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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;

import genesis.infrastructure.AppManager;
import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import spoon.Launcher;
import spoon.support.reflect.declaration.CtConstructorImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

public class TreeDiffer {
	MyCtNode t1, t2;
	MyCtNode lastT1, lastT2;
	boolean diff;
	
	public TreeDiffer(MyCtNode a, MyCtNode b) {
		this.t1 = a;
		this.t2 = b;
		lastT1 = null;
		lastT2 = null;
		diff = !this.t1.treeEquals(this.t2);
	}
	
	public boolean diff() {
		return diff;
	}
	
	public boolean narrowDown(boolean tolerentDiff) {
		if (!diff) return false;
		if (!t1.isCollection()) {
			if (!t1.nodeEquals(t2)) return false;
			int n = t1.getNumChildren();
			MyCtNode a = null, b = null;
			for (int i = 0; i < n; i++) {
				if (!t1.getChild(i).treeEquals(t2.getChild(i))) {
					if (a != null) {
						// XXX: If a for loop variable declaration changed, its body may appear different but it does not matter.
						// Here is the code to handle this case. Because toString() is not very reliable if parent is not set.
						// I use a try catch to avoid potential caveat.
						boolean skip = false;
						try {
							if (t1.getChild(i).toString().equals(t2.getChild(i).toString()))
								skip = true;
						}
						catch (Exception e) {
						}
						if (skip) continue;
						return false;
					}
					a = t1.getChild(i);
					b = t2.getChild(i);
				}
			}
			if (!t1.isCollection()) lastT1 = t1;
			if (!t2.isCollection()) lastT2 = t2;
			t1 = a;
			t2 = b;
		}
		else {
			int n = t1.getNumChildren();
			int m = t2.getNumChildren();
			if (n == 0 || m == 0) return false;
			int i1 = 0, j1 = 0;
			while (i1 < n && j1 < m) {
				if (!t1.getChild(i1).treeEquals(t2.getChild(j1)))
					break;
				i1 ++;
				j1 ++;
			}
			int i2 = n, j2 = m;
			while (i1 < i2 && j1 < j2) {
				if (!t1.getChild(i2-1).treeEquals(t2.getChild(j2-1)))
					break;
				i2 --;
				j2 --;
			}
			// We are going to have an intermediate snippet that consider the statement before
			// and the statement after
			if (i1 + (n - i2) > 2) {
				if (i1 > 0) {
					i1 --;
					j1 --;
				}
				if (i2 < n) {
					i2 ++;
					j2 ++;
				}
			}
			else if (i1 + (n - i2) == 2 && i2 < n) {
				// We are going to also create a snippet for just the statement after if possible
				i2 ++;
				j2 ++;
			}
			// XXX: So far we do not create pure empty before
			if (i2 - i1 == 0)
				return false;
			if (i2 - i1 == 1 && j2 - j1 == 1 && (n == 1 || m == 1)) {
				if (!t1.isCollection()) lastT1 = t1;
				if (!t2.isCollection()) lastT2 = t2;
				t1 = t1.getChild(i1);
				t2 = t2.getChild(j1);
			}
			else {
				if (i1 == 0 && i2 == n) {
					if (tolerentDiff) {
						if (!t1.isCollection()) lastT1 = t1;
						if (!t2.isCollection()) lastT2 = t2;
						t1 = t1.getChild(i1);
						t2 = t2.getChild(j1);
					}
					else 
						return false;
				}
				else {
					// We are going to create a snippet for the collection, not sure whether this is the right way
					ArrayList<Object> tmp1, tmp2;
					tmp1 = new ArrayList<Object>();
					for (int i = i1; i < i2; i++) {
						tmp1.add(t1.getChild(i).getRawObject());
					}
					if (lastT1 != null)
						t1 = new MyCtNode(tmp1, false, lastT1.getRawObject());
					else
						t1 = new MyCtNode(tmp1, false);
					tmp2 = new ArrayList<Object>();
					for (int j = j1; j < j2; j++)
						tmp2.add(t2.getChild(j).getRawObject());
					if (lastT2 != null)
						t2 = new MyCtNode(tmp2, false, lastT2.getRawObject());
					else
						t2 = new MyCtNode(tmp2, false);
				}
			}
		}
		return true;
	}
	
	public MyCtNode getT1() {
		return t1;
	}
	
	public MyCtNode getT2() {
		return t2;
	}
	
	public static void main(String[] args) {
		if (args.length < 6) System.exit(1);
		boolean rawparse = false;
		if (args[0].equals("-raw") || args[1].equals("-raw"))
			rawparse = true;
		MyCtNode n1, n2;
		if (rawparse) {
			Launcher l1 = new Launcher();
			n1 = AppManager.spoonCompile(l1, args[2], ".");
			Launcher l2 = new Launcher();
			n2 = AppManager.spoonCompile(l2, args[3], ".");
		}
		else {
			AppManager parser1 = new AppManager(args[0]);
			AppManager parser2 = new AppManager(args[1]);
			if (parser1.getBuildEngineKind() == null || parser2.getBuildEngineKind() == null) {
				System.out.println("Unable to detect the build engine. Only support Ant, Maven, and Gradle.");
				System.exit(1);
			}
			n1 = parser1.getCtNode(args[2], true);
			if (n1 == null) {
				System.out.println("Compilation of the repo " + args[0] + " failed at some point!");
				System.exit(1);
			}
			n2 = parser2.getCtNode(args[3], true);
			if (n2 == null) {
				System.out.println("Compilation of the repo " + args[1] + " failed at some point!");
				System.exit(1);
			}
		}
		
		TreeDiffer differ = new TreeDiffer(n1, n2);
		if (!differ.diff()) {
			System.out.println("AST Trees are same!");
			System.exit(1);
		}
		
		boolean pass = false;
		do {
			HashSet<MyNodeSig> s1 = differ.getT1().nodeSigSet();
			HashSet<MyNodeSig> s2 = differ.getT2().nodeSigSet();
			if (!s1.contains(new MyNodeSig(CtMethodImpl.class, false)) && !s1.contains(new MyNodeSig(CtConstructorImpl.class, false)))
				if (!s2.contains(new MyNodeSig(CtMethodImpl.class, false)) && !s2.contains(new MyNodeSig(CtConstructorImpl.class, false)))
					pass = true;
		}
		while (!pass && differ.narrowDown(false));
		
		if (!pass) {
			System.out.println("Difference is more than 5 statements or have non-statement difference");
			System.exit(1);
		}
		
		String fname1 = args[4], fname2 = args[5];
		try {
			ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(fname1));
			n1.writeToStream(os);
			os.close();
			os = new ObjectOutputStream(new FileOutputStream(fname2));
			n2.writeToStream(os);
			os.close();
		}
		catch (IOException e) {
			e.printStackTrace();
			try {
				Files.delete(Paths.get(fname1));
				Files.delete(Paths.get(fname2));
			}
			catch (Exception e1) { }
			System.out.println("Cannot serialize parsed tree to " + fname1 + " and/or " + fname2);
			System.exit(1);
		}
		
		//Launcher l = new Launcher();
		MyCtNode a1 = null, a2 = null;
		try {
			ObjectInputStream is = new ObjectInputStream(new FileInputStream(fname1));
			a1 = MyCtNode.readFromStream(is);
			is.close();
			is = new ObjectInputStream(new FileInputStream(fname2));
			a2 = MyCtNode.readFromStream(is);
			is.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			try {
				Files.delete(Paths.get(fname1));
				Files.delete(Paths.get(fname2));
			}
			catch (Exception e1) { }
			System.out.println("Cannot deserialize the parse tree from " + fname1 + " and/or " + fname2);
			System.exit(1);
		}
		
		if (!a1.treeEquals(n1) || !a2.treeEquals(n2)) {
			try {
				Files.delete(Paths.get(fname1));
				Files.delete(Paths.get(fname2));
			}
			catch (Exception e1) { }
			System.out.println("Deserilized trees are different!");
			System.exit(1);
		}
	}
}
