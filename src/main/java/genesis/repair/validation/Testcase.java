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
package genesis.repair.validation;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Testcase {
	
	public final String testClass;
	public final String testName;
	
	public Testcase(String testClass, String testCase) {
		this.testClass = testClass;
		this.testName = testCase;
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(testClass).append(testName).hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Testcase))
			return false;
		if (this == obj) return true;
		Testcase o = (Testcase) obj;
		return new EqualsBuilder().append(testClass, o.testClass).append(testName, o.testName).build();
	}
	
	@Override
	public String toString() {
		return testClass + "#" + testName;
	}
}
