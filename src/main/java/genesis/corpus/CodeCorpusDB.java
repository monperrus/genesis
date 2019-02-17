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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

import genesis.GenesisException;

public class CodeCorpusDB {
	
	HashMap<Integer, CorpusApp> apps;
	Vector<CorpusPatch> patches;
	Vector<Integer> workList;
	
	static {
		/* XXX: for new version, this is not required
		try {
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new GenesisException("Failed to initialize mysql driver!");
		}*/
	}
	
	public CodeCorpusDB() {
		this("jdbc:mysql://127.0.0.1/genesis?serverTimezone=UTC&useSSL=false", "root", "genesis");
	}

	public CodeCorpusDB(String url, String user, String passwd) {
		Connection conn = null;
		Statement stmt = null;
		apps = new HashMap<Integer, CorpusApp>();
		patches = new Vector<CorpusPatch>();
		try {
			conn = DriverManager.getConnection(url, user, passwd);
			stmt = conn.createStatement();
			ResultSet res = stmt.executeQuery("SELECT id, github_reponame, github_accname, github_url FROM application");
			while (res.next()) {
				String reponame = res.getString("github_reponame");
				String accname = res.getString("github_accname");
				String githubUrl = res.getString("github_url");
				apps.put(res.getInt("id"), new CorpusApp(reponame, accname, githubUrl));
			}
			res = stmt.executeQuery("SELECT id, app_id, github_prepatch_rev, github_postpatch_rev, ast_prepatch_path, ast_postpatch_path, is_null_fix, for_npe_test, is_bound_fix, for_oob_test, is_cast_fix, for_cce_test FROM patch");
			while (res.next()) {
				Integer appId = res.getInt("app_id");
				String preRev = res.getString("github_prepatch_rev");
				String postRev = res.getString("github_postpatch_rev");
				String prePath = res.getString("ast_prepatch_path");
				String postPath = res.getString("ast_postpatch_path");
				Boolean isNullFix = res.getBoolean("is_null_fix");
				Boolean forNpeTest = res.getBoolean("for_npe_test");
				Boolean isBoundFix = res.getBoolean("is_bound_fix");
				Boolean forOobTest = res.getBoolean("for_oob_test");
				Boolean isCastFix = res.getBoolean("is_cast_fix");
				Boolean forCceTest = res.getBoolean("for_cce_test");
				patches.add(new CorpusPatch(appId, preRev, postRev, prePath, postPath, isNullFix, forNpeTest, isBoundFix, forOobTest, isCastFix, forCceTest));
			}
			res.close();
			stmt.close();
			conn.close();
		}
		catch (SQLException e) {
			e.printStackTrace();
			throw new GenesisException("SQL execution failed.");
		}
		finally {
			try {
				if (stmt != null)
					stmt.close();
			}
			catch (SQLException e2) { }
			try {
				if (conn != null)
					conn.close();
			}
			catch (SQLException e3) { }
		}
		
		initFetch(0);
	}
	
	public int size() {
		return patches.size();
	}
	
	public int appSize() {
		return apps.size();
	}
	
	public int initFetch(int mode) {
		workList = new Vector<Integer>();
		if (mode != 0) {
			for (int i = patches.size() - 1; i >= 0; i--) {
				if (mode == 1) {
					if (patches.get(i).forNpeTest)
						workList.add(i);
				}
				else if (mode == 2) {
					if (patches.get(i).forOobTest)
						workList.add(i);
				}
				else if (mode == 3) {
					if (patches.get(i).forCceTest)
						workList.add(i);
				}
				else if (mode == 4) {
					if (patches.get(i).forAnyTest)
						workList.add(i);
				}
			}
		}
		for (int i = patches.size() - 1; i >= 0; i--) {
			if (mode != 0) {
				if (mode == 1) {
					if ((!patches.get(i).isNullFix) || (patches.get(i).forNpeTest))
						continue;
				}
				else if (mode == 2) {
					if ((!patches.get(i).isBoundFix) || (patches.get(i).forOobTest))
						continue;
				}
				else if (mode == 3) {
					if ((!patches.get(i).isCastFix) || (patches.get(i).forCceTest))
						continue;
				}
				else if (mode == 4) {
					if ((!patches.get(i).isAnyFix) || (patches.get(i).forAnyTest))
						continue;
				}
			}
			workList.add(i);
		}
		return workList.size();
	}
	
	public int shuffleInitFetch(int mode) {
		initFetch(mode);
		Random generator = new Random(0);
		for (int i = 1; i < workList.size(); i++) {
			if (mode != 0) {
				if (mode == 1) {
					if (patches.get(workList.get(i)).forNpeTest) continue;
				}
				else if (mode == 2) {
					if (patches.get(workList.get(i)).forOobTest) continue;
				}
				else if (mode == 3) {
					if (patches.get(workList.get(i)).forCceTest) continue;
				}
				else if (mode == 4) {
					if (patches.get(workList.get(i)).forAnyTest) continue;
				}
			}
			int j = 0;
			do {
				j = generator.nextInt(workList.size());
			}
			while (((mode == 1) && patches.get(workList.get(j)).forNpeTest) ||
					((mode == 2) && patches.get(workList.get(j)).forOobTest) ||
					((mode == 3) && patches.get(workList.get(j)).forCceTest) ||
					((mode == 4) && patches.get(workList.get(j)).forAnyTest));
			if (i != j) {
				int tmp = workList.get(i);
				workList.set(i, workList.get(j));
				workList.set(j, tmp);
			}
		}
		return workList.size();
	}
	
	public ArrayList<CorpusPatch> fetch(int num) {
		if (workList == null)
			return null;
		ArrayList<CorpusPatch> ret = new ArrayList<CorpusPatch>(); 
		for (int i = 0; i < num; i++) {
			if (workList.size() == 0)
				break;
			int idx = workList.get(workList.size() - 1);
			CorpusPatch patch = patches.get(idx);
			ret.add(patch);
			workList.remove(workList.size() - 1);
		}
		return ret;
	}
	
	public HashMap<Integer, CorpusApp> getApplications() {
		return apps;
	}
}
