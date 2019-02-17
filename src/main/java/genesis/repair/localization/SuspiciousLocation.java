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
package genesis.repair.localization;

import java.util.Scanner;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SuspiciousLocation {

	private final String srcPath;
	private final int line, col; /* -1 in col means don't care */
	private final double suspiciousness;
	
	public SuspiciousLocation(String srcPath, int line, int col) {
		this.srcPath = srcPath;
		this.line = line;
		this.col = col;
		this.suspiciousness = 0;
	}

	public SuspiciousLocation(String srcPath, int line, int col, double suspiciousness) {
		this.srcPath = srcPath;
		this.line = line;
		this.col = col;
		this.suspiciousness = suspiciousness;
	}
	
	public String getSourcePath() {
		return this.srcPath;
	}
	
	public int getLine() {
		return this.line;
	}
	
	public int getColumn() {
		return this.col;
	}
	
	public double getSuspiciousness() {
		return suspiciousness;
	}

	public static SuspiciousLocation createFromStrLine(String str) {
		Scanner s = new Scanner(str);
		String srcPath = s.next();
		int line = s.nextInt();
		int col = s.nextInt();
		s.close();
		return new SuspiciousLocation(srcPath, line, col);
	}
	
	@Override
	public String toString() {
		return srcPath + " " + line + " " + col + " " + suspiciousness;
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(srcPath).append(line).append(col).build();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SuspiciousLocation)) return false;
		if (this == o) return true;
		SuspiciousLocation other = (SuspiciousLocation)o;
		return new EqualsBuilder().append(srcPath, other.srcPath)
				.append(line, other.line).append(col, other.col).build();
	}
}
