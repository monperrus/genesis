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
package genesis.infrastructure;

import java.io.File;
import java.util.Random;

public class UntarDir {

	File tarf;
	String parentDir;
	String tarDir;
	int flag;

	private static String getExt(String path) {
		int idx = path.lastIndexOf(".");
		if (idx == -1) return "";
		return path.substring(idx + 1);
	}

	public UntarDir(String path) {
		tarf = new File(path);
		if (getExt(path).equals("jar"))
			this.flag = 0;
		else if (getExt(path).equals("gz") || getExt(path).equals("tgz"))
			this.flag = 1;
		if (tarf.exists()) {
			parentDir = tarf.getParentFile().getAbsolutePath();
		}
		else {
			parentDir = null;
			tarf = null;
		}
	}

	public String untar() {
		if (tarf == null) return null;
		Random rnd = new Random(System.identityHashCode(this));
		tarDir = parentDir + "/__tmp" + rnd.nextInt(100000);
		int ret = ExecShellCmd.runCmd("rm -rf " + tarDir);
		assert(ret == 0);
		ret = ExecShellCmd.runCmd("mkdir " + tarDir);
		assert(ret == 0);
		if (flag == 0)
			ret = ExecShellCmd.runCmd("jar xf " + tarf.getAbsolutePath(), tarDir);
		else if (flag == 1) {
			System.out.println("tar xzf " + tarf.getAbsolutePath());
			ret = ExecShellCmd.runCmd("tar xzf " + tarf.getAbsolutePath(), tarDir);
		}
		assert(ret == 0);

		return tarDir;
	}

	public void clean() {
		ExecShellCmd.runCmd("rm -rf " + tarDir);
		tarf = null;
		parentDir = null;
		tarDir = null;
	}

}
