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
package genesis.utils;

import genesis.Config;

public class CombNo {

	public final static int comblimit = 25;
	
	static long c[][];
	
	static {
		c = new long[comblimit][comblimit];
		c[0][0] = 1;
		for (int i = 1; i < comblimit; i++)
			c[0][i] = 0;
		for (int i = 1; i < comblimit; i++) {
			c[i][0] = 1;
			for (int j = 1; j <= i; j++) {
				c[i][j] = c[i-1][j] + c[i-1][j-1];
			}
			for (int j = i + 1; j < comblimit; j++)
				c[i][j] = 0;
		}
	}
	
	public static long C(int n, int k) {
		if (n >= comblimit)
			return Config.costlimit;
		if (k > n) return 0;
		return c[n][k];
	}

	public static long intPow(int x, int y) {
		long ret = 1;
		for (int i = 0; i < y; i ++) { 
			ret *= x;
			if (ret > Config.costlimit) return Config.costlimit;
		}
		return ret;
	}

	public static long factor(int n) {
		long ret = 1;
		for (int i = 1; i < n; i++) {
			ret *= i;
			if (ret > Config.costlimit) return Config.costlimit;
		}
		return ret;
	}

}
