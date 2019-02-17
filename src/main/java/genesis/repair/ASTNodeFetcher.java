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
package genesis.repair;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import genesis.node.MyCtNode;
import genesis.node.MyNodeSig;
import genesis.repair.localization.SuspiciousLocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.Filter;

public class ASTNodeFetcher {
	
	WorkdirManager manager;
	HashMap<String, MyCtNode> rootCache;
	HashMap<SuspiciousLocation, HashMap<MyCtNode, HashSet<MyNodeSig>>> fetchCache;
	
	public ASTNodeFetcher(WorkdirManager manager) {
		this.manager = manager;
		this.rootCache = new HashMap<String, MyCtNode>();
		this.fetchCache = new HashMap<>();
	}

	class LocationScanner extends CtScanner {
		SuspiciousLocation loc;
		HashMap<MyNodeSig, Integer> insideCnt;
		HashMap<CtElement, HashSet<MyNodeSig>> res;
		
		LocationScanner(SuspiciousLocation loc) {
			this.loc = loc;
			this.insideCnt = new HashMap<MyNodeSig, Integer>();
			this.res = new HashMap<CtElement, HashSet<MyNodeSig>>();
		}
		
		class NodesEqFilter implements Filter<CtElement> {
			
			private Set<CtElement> nodes;
			
			public NodesEqFilter(Set<CtElement> nodes) {
				this.nodes = nodes;
			}
			
			@Override
			public boolean matches(CtElement ele) {
				return nodes.contains(ele);
			}
		}
		
		@Override
		public void enter(CtElement ele) {
			MyNodeSig sig = new MyNodeSig(ele, false);
			if (!insideCnt.containsKey(sig))
				insideCnt.put(sig, 1);
			else
				insideCnt.put(sig, insideCnt.get(sig) + 1);
		}
		
		@Override
		public void exit(CtElement ele) {
			MyNodeSig sig = new MyNodeSig(ele, false);
			insideCnt.put(sig, insideCnt.get(sig) - 1);
			// XXX: So far we only count statement, we should remove this limitation in future
			if (ele instanceof CtStatement) {
				SourcePosition pos = ele.getPosition();
				if (pos != null) {
					int line = pos.getLine();
					int col = pos.getColumn();
					/* If no column, find the largest nodes on that
					   line: defect localization tools do not seem to
					   give columns
					*/
					List<CtElement> subResNodes =
					    ele.getElements(new NodesEqFilter(res.keySet()));
					if (loc.getLine() == line && (loc.getColumn() == -1 || loc.getColumn() == col)) {
						res.keySet().removeAll(subResNodes);
						HashSet<MyNodeSig> insides = new HashSet<>();
						for (MyNodeSig sig1 : insideCnt.keySet()) {
							if (insideCnt.get(sig1) != 0)
								insides.add(sig1);
						}
						res.put(ele, insides);
					}
				}
			}
		}
	}
	
	private boolean fetchImpl(SuspiciousLocation loc) {
		String srcPath = loc.getSourcePath();
		if (!rootCache.containsKey(srcPath))
			rootCache.put(srcPath, manager.getRootASTNode(srcPath));
		MyCtNode root = rootCache.get(srcPath);
		LocationScanner scanner = new LocationScanner(loc);
		root.acceptScanner(scanner);
		if (scanner.res.size() == 0)
			return false;
		else {
			HashMap<MyCtNode, HashSet<MyNodeSig>> res = new HashMap<>();
			for (Map.Entry<CtElement, HashSet<MyNodeSig>> e : scanner.res.entrySet()) {
				MyCtNode node = new MyCtNode(e.getKey(), false);
				res.put(node, e.getValue());
			}
			fetchCache.put(loc, res);
			return true;
		}
	}

	public HashMap<MyCtNode, HashSet<MyNodeSig>> fetch(SuspiciousLocation loc) {
		if (!fetchCache.containsKey(loc))
			fetchImpl(loc);
		return fetchCache.get(loc);
	}

	public MyCtNode fetchRoot(SuspiciousLocation loc) {
		if (!rootCache.containsKey(loc.getSourcePath()))
			fetchImpl(loc);
		return rootCache.get(loc.getSourcePath());
	}
}
