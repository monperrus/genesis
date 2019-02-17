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
package genesis.corpus;

public class CorpusPatch {

	public int appId;
	public String preRev;
	public String postRev;
	public String prePath;
	public String postPath;
	public boolean isNullFix;
	public boolean forNpeTest;
	public boolean isBoundFix;
	public boolean forOobTest;
	public boolean isCastFix;
	public boolean forCceTest;
	public boolean isAnyFix;
	public boolean forAnyTest;
	
	public CorpusPatch(Integer appId, String preRev, String postRev, String prePath, 
			String postPath, boolean isNullFix, boolean forNpeTest, boolean isBoundFix, 
			boolean forOobTest, boolean isCastFix, boolean forCceTest) {
		this.appId = appId;
		this.preRev = preRev;
		this.postRev = postRev;
		this.prePath = prePath;
		this.postPath = postPath;
		this.isNullFix = isNullFix;
		this.forNpeTest = forNpeTest;
		this.isBoundFix = isBoundFix;
		this.forOobTest = forOobTest;
		this.isCastFix = isCastFix;
		this.forCceTest = forCceTest;
		this.isAnyFix = isNullFix || isBoundFix || isCastFix;
		this.forAnyTest = forNpeTest || forOobTest || forCceTest;
	}

}
