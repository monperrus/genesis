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
package genesis.rewrite;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import genesis.GenesisException;
import genesis.infrastructure.AppManager;
import genesis.learning.TreeDiffer;
import genesis.node.MyCtNode;

public class RewritePassManager {
	
	ArrayList<CodePairRewritePass> passes;
	MyCtNode before, after;
	MyCtNode beforeSnippet, afterSnippet;
	MyCtNode resBeforeSnippet, resAfterSnippet;
	String resBeforeStr, resAfterStr;
	
	public RewritePassManager() {
		passes = new ArrayList<CodePairRewritePass>();
		before = null;
		after = null;
		beforeSnippet = null;
		afterSnippet = null;
		resBeforeSnippet = null;
		resAfterSnippet = null;
		resBeforeStr = null;
		resAfterStr = null;
	}
	
	public void addPass(CodePairRewritePass pass) {
		passes.add(pass);
	}
	
	public boolean run(MyCtNode before, MyCtNode after) {
		boolean ret = false;
		this.before = before;
		this.after = after;
		TreeDiffer differ = new TreeDiffer(before, after);
		do {
			MyCtNode t1 = differ.getT1();
			// FIXME: My CodeRewriter just does not work for this shit
			// I really need to fix this when I get time
			if (t1.isCollection())
				if (t1.getNumChildren() == 0)
					break;
			if (t1.isTrait())
				break;
			MyCtNode t2 = differ.getT2();
			// FIXME: My CodeRewriter just does not work for this shit
			// I really need to fix this when I get time
			if (t2.isCollection())
				if (t2.getNumChildren() == 0)
					break;
			if (t2.isTrait())
				break;
			beforeSnippet = t1;
			afterSnippet = t2;
		}
		while (differ.narrowDown(false));
		resBeforeSnippet = beforeSnippet;
		resAfterSnippet = afterSnippet;
		for (int i = 0; i < passes.size(); i++) {
			CodePairRewritePass p = passes.get(i);
			boolean changed = p.run(resBeforeSnippet, resAfterSnippet);
			if (changed) {
				resBeforeSnippet = p.getResultBefore();
				resAfterSnippet = p.getResultAfter();
				ret = true;
			}
		}
		return ret;
	}
	
	private void computeResultCodeString() {
		CodeRewriter r1 = new CodeRewriter(before);
		r1.addMapping(beforeSnippet, resBeforeSnippet);
		resBeforeStr = r1.rewrite();
		CodeRewriter r2 = new CodeRewriter(after);
		r2.addMapping(afterSnippet, resAfterSnippet);
		resAfterStr = r2.rewrite();
	}
	
	public String getBeforeCodeString() {
		if (resBeforeStr == null)
			computeResultCodeString();
		return resBeforeStr;
	}
	
	public String getAfterCodeString() {
		if (resAfterStr == null)
			computeResultCodeString();
		return resAfterStr;
	}
	
	public void writeToFile(String fileBefore, String fileAfter) {
		if (resBeforeStr == null)
			computeResultCodeString();
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(fileBefore));
			writer.write(resBeforeStr);
			writer.close();
			writer = new BufferedWriter(new FileWriter(fileAfter));
			writer.write(resAfterStr);
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
			throw new GenesisException(e);
		}
	}

	public static void main(String args[]) {
		if (args.length < 6) System.exit(1);
		AppManager parser1 = new AppManager(args[0]);
		AppManager parser2 = new AppManager(args[1]);
		if (parser1.getBuildEngineKind() == null || parser2.getBuildEngineKind() == null) {
			System.out.println("Unable to detect the build engine. Only support Ant, Maven, and Gradle.");
			System.exit(1);
		}
		MyCtNode n1 = parser1.getCtNode(args[2], true);
		if (n1 == null) {
			System.out.println("Compilation of the repo " + args[0] + " failed at some point!");
			System.exit(1);
		}
		MyCtNode n2 = parser2.getCtNode(args[3], true);
		if (n2 == null) {
			System.out.println("Compilation of the repo " + args[1] + " failed at some point!");
			System.exit(1);
		}
		
		RewritePassManager m = new RewritePassManager();
		m.addPass(new VarDeclEliminationPass());
		m.addPass(new CommutativityRewritePass());
		m.addPass(new LiteralRewritePass());
		m.addPass(new CodePairRewriteWrapperPass(new CodeStyleNormalizationPass()));
		boolean changed = m.run(n1, n2);
		if (changed) 
			m.writeToFile(args[4], args[5]);
	}
}
