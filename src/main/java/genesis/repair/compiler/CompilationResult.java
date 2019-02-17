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
package genesis.repair.compiler;

import java.util.List;
import java.util.Map;
/**
 * 
 * @author Matias Martinez,  matias.martinez@inria.fr
 *
 */
public class CompilationResult {

	private Map<String, byte[]> byteCodes;

	private List<String> errorList = null;
	
	public CompilationResult(Map<String, byte[]> bytecodes2,List<String> errorList) {
		byteCodes = bytecodes2;
		this.errorList = errorList; 
	}

	public Map<String, byte[]> getByteCodes() {
		return byteCodes;
	}

	public void setByteCodes(Map<String, byte[]> byteCodes) {
		this.byteCodes = byteCodes;
	}

	public boolean compiles() {
		return errorList == null || errorList.isEmpty();
	}
	
	@Override
	public String toString() {
		return "CompilationResult: byteCodes=" + byteCodes.size()+" errors (" + errorList.size()+ ") "+errorList + "]";
	}

	public List<String> getErrorList() {
		return errorList;
	}

	public void setErrorList(List<String> errorList) {
		this.errorList = errorList;
	}

}
