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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import genesis.Config;
import genesis.analysis.StaticAnalyzer;
import genesis.node.MyCtNode;
import genesis.node.MyNodeTrait;
import genesis.schema.VarTypeContext;
import spoon.reflect.factory.Factory;

public class TraitGenerator extends VarGenerator implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	HashSet<MyNodeTrait> allowedTrait;

	protected TraitGenerator() {
		allowedTrait = new HashSet<MyNodeTrait>();
	}

	public static VarGenerator createGenerator(List<MyCtNode> trees, List<MyCtNode> befores) {
		TraitGenerator ret = new TraitGenerator();
		for (int i = 0; i < trees.size(); i++) {
			MyCtNode n = trees.get(i);
			if (!n.isTrait()) return null;
			if (Config.collapseTextMsg)
				if (n.nodeTrait().isTextMessage()) {
					ret.allowedTrait.add(new MyNodeTrait(Config.textMsgToken));
					continue;
				}
			if (n.isLocalVarNameNode()) {
				StaticAnalyzer ana = new StaticAnalyzer(befores.get(i));
				if (!ana.inBeforeVarName(n.nodeTrait().toString())) {
					ret.allowedTrait.add(new MyNodeTrait(Config.newVarToken));
					continue;
				}
			}
			ret.allowedTrait.add(n.nodeTrait());
		}
		return ret;
	}

	@Override
	public ArrayList<MyCtNode> generate(MyCtNode before, Factory f, VarTypeContext context) {
		ArrayList<MyCtNode> ret = new ArrayList<MyCtNode>();
		for (MyNodeTrait t : allowedTrait)
			ret.add(t.convertToNodeWithClone(f));
		return ret;
	}

	@Override
	public boolean covers(MyCtNode before, MyCtNode after, VarTypeContext context) {
		if (!after.isTrait()) return false;
		if (Config.collapseTextMsg)
			if (after.nodeTrait().isTextMessage())
				return allowedTrait.contains(new MyNodeTrait(Config.textMsgToken));
		if (after.isLocalVarNameNode()) {
			StaticAnalyzer ana = new StaticAnalyzer(before);
			if (!ana.inBeforeVarName(before.nodeTrait().toString()))
				if (allowedTrait.contains(new MyNodeTrait(Config.newVarToken)))
					return true;
		}
		return allowedTrait.contains(after.nodeTrait());
	}

	@Override
	public long estimateCost(MyCtNode before) {
		return allowedTrait.size();
	}

	@Override
	public boolean generatorEquals(VarGenerator g) {
		if (!(g instanceof TraitGenerator)) return false;
		TraitGenerator a = (TraitGenerator) g;
		return allowedTrait.equals(a.allowedTrait);
	}

	@Override
	public String toString() {
		return allowedTrait.toString();
	}
}
