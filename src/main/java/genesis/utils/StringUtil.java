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

public abstract class StringUtil {

	public static boolean isTextMessage(String str) {
		// Very primitive count, if it has more than 1 whitespace separator and it does not have special token %, more than half is letter, then it is text msg
		String str1 = str.trim();
		int whiteCnt = 0;
		int letterCnt = 0;
		for (int i = 0; i < str1.length(); i++) {
			char c1 = str1.charAt(i);
			if (i != 0) {
				char c0 = str1.charAt(i-1);
				if (Character.isWhitespace(c1) && !Character.isWhitespace(c0))
					whiteCnt ++;
			}
			if (c1 == '%') return false;
			if (Character.isLetter(c1)) letterCnt ++;
		}
		return (whiteCnt >= 1) && (letterCnt > str1.length() / 2);
	}
	
	public static String getLongestCommonSubStr(String s1, String s2) {
		// OK, compute LCS should be O(N) with suffix tree, but I am too lazy to write that shit
		// I am happy with this stupid O(N^2) that I come up in 5 min
		String ret = "";
		for (int i = 0; i < s2.length(); i++) {
			int cnt = 0;
			int j;
			for (j = 0; j < s1.length(); j++) {
				if (i + j >= s2.length()) break;
				if (s1.charAt(j) == s2.charAt(i + j))
					cnt ++;
				else {
					if (ret.length() < cnt)
						ret = s1.substring(j - cnt, j);
					cnt = 0;
				}
			}
			if (ret.length() < cnt) {
				ret = s1.substring(j - cnt, j);
			}
		}
		return ret;
	}
}
