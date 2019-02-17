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
package genesis.transform;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import genesis.generator.TraitGenerator;
import genesis.generator.VarGenerator;
import genesis.schema.TransformSchema;

public class CodeTransform implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public CodeTransform(TransformSchema schema, Map<Integer, VarGenerator> vargens) {
		this.schema = schema;
		this.vargens = vargens;
	}

	TransformSchema schema;
	Map<Integer, VarGenerator> vargens;

	public boolean generatorEquals(CodeTransform g) {
		if (!g.schema.schemaEquals(schema)) return false;
		if (g.vargens.size() != vargens.size()) return false;
		for (Integer vid : vargens.keySet()) {
			if (!g.vargens.containsKey(vid)) return false;
			if (!vargens.get(vid).generatorEquals(g.vargens.get(vid))) return false;
		}
		return true;
	}

	@Override
	public String toString() {
		String ret = schema.toString();
		ret += "\n";
		ret += vargens.toString();
		return ret;
	}

	public boolean canGenStatementNodeOnly() {
		return schema.canGenStatementNodeOnly();
	}

	public boolean canGenExpressionNodeOnly() {
		return schema.canGenExpressionNodeOnly();
	}

	public boolean canGenClassNode() {
		return schema.canGenClassNode();
	}
	
	public boolean canGenTraitOnly() {
		int idx = -1;
		idx = schema.getOnlyVarIdxInPost();
		if (vargens.containsKey(idx))
			if (vargens.get(idx) instanceof TraitGenerator)
				return true;
		return false;
	}
	
}
