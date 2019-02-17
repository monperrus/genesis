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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import genesis.GenesisException;
import genesis.node.MyCtNode;
import genesis.utils.Pair;

public class CodeCorpusDir {
	
	ArrayList<Pair<MyCtNode, MyCtNode>> codePairs;
	ArrayList<App> apps;
	int totalRevs;

	class App {
		String revfname;
		ArrayList<Pair<String, String>> revs;
	};

	public CodeCorpusDir(String path) {
		this(path, -1, -1);
	}
	
	// We are going to parse the code corpus from a directory
	// written down by our own crawler
	public CodeCorpusDir(String path, int _debugU, int _debugI) {
		this.codePairs = new ArrayList<Pair<MyCtNode, MyCtNode>>();
		this.apps = new ArrayList<App>();
		File dir = new File(path);
		if (!dir.isDirectory())
			throw new GenesisException(path + " is not a corpur directory!");
		File[] files = dir.listFiles();
		for (int i = 0; i < files.length; i ++)
			for (int j = i + 1; j < files.length; j++)
				if (files[i].getPath().compareTo(files[j].getPath()) > 0) {
					File tmp = files[i];
					files[i] = files[j];
					files[j] = tmp;
				}
		totalRevs = 0;
		int cnt = 0;
		for (File f1 : files) {
			String fname = f1.getName();
			if (!f1.isDirectory() && fname.endsWith("_revs")) {
				System.out.println("Discovering a revision log-file: " + fname);
				// I am going to suppose this is a revision file
				List<String> revs = null;
				try {
					revs = Files.readAllLines(Paths.get(f1.getPath()));
				}
				catch (IOException e) {
					e.printStackTrace();
					throw new GenesisException("Cannot parse " + path);
				}
				App tmp = new App();
				tmp.revfname = fname;
				tmp.revs = new ArrayList<Pair<String, String>>();

				String pathPrefix = path + "/" +
						fname.substring(0, fname.length() - 5) + "_po/";

				for (String rev : revs) {
					String aname = pathPrefix + "a_" + rev + ".po";
					String bname = pathPrefix + "b_" + rev + ".po";
					tmp.revs.add(new Pair<String, String>(aname, bname));
					totalRevs ++;
				}
				apps.add(tmp);
			}
		}

		System.out.println("Total number of revisions in the directory: " + totalRevs);

		for (App app : apps) {
			for (Pair<String, String> revp : app.revs) {
				String aname = revp.x;
				String bname = revp.y;
				if (_debugU != -1 && codePairs.size() != _debugU && codePairs.size() != _debugI) {
					codePairs.add(new Pair<MyCtNode, MyCtNode>(null, null));
					continue;
				}
				/*if ((aname.indexOf("021d6722") != -1) || (bname.indexOf("021d6722") != -1))
					System.out.println("The id: " + codePairs.size());
				else if (cnt > 20) continue;*/
				MyCtNode a1 = CorpusUtils.parseJavaAST(aname);
				MyCtNode a2 = CorpusUtils.parseJavaAST(bname);
				/*try {
					ObjectInputStream is = new ObjectInputStream(new FileInputStream(bname));
					a1 = MyCtNode.readFromStream(is);
					is.close();
					is = new ObjectInputStream(new FileInputStream(aname));
					a2 = MyCtNode.readFromStream(is);
					is.close();
				}
				catch (Exception e) {
					e.printStackTrace();
					throw new GenesisException("Exception happens when deserializing "
							+ aname + " and " + bname);
				}*/
 
				codePairs.add(new Pair<MyCtNode, MyCtNode>(a1, a2));
				cnt ++;
				if (cnt % 100 == 0)
					System.out.println("Processed " + cnt + " files!");
				//if (cnt > 60)
				//	return;
			}
		}
	}

	// We are going to parse the code corpus from a directory
	// written down by our own crawler
	/*public CodeCorpus(String path) {
		this(path, new FactoryImpl(new DefaultCoreFactory(),
				new StandardEnvironment()));
	}*/

	public ArrayList<Pair<MyCtNode, MyCtNode>> getCodePairs() {
		return codePairs;
	}
}
