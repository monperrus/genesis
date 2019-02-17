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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import genesis.Config;
import genesis.GenesisException;
import genesis.infrastructure.ExecShellCmd;

public class TestcaseExecutor {

	public static String RunnerSEP = "__GENESISRUNFINISH:";
	public static String ThrowableSEP = "__GENESIS_THROWABLE:";
	
	Class<?> testRunnerClass;
	String workDir;
	ExecShellCmd ecmd;
	Process p;
	long startTime;
	long endTime;
	List<Testcase> cases;
	String classPath;
	int exitCode;
	String out;
	
	class TestWorkerThread extends Thread {

		Process p;
		int exitCode;
		
		public TestWorkerThread(Process p) {
			super();
			this.p = p;
		}
		
		@Override
		public void run() {
			try {
				exitCode = p.waitFor();
			}
			catch (InterruptedException ignore) {
			}
		}
		
	}

	public TestcaseExecutor(String testClassPath, List<Testcase> cases, String workDir) {
		if (testClassPath.contains("scalatest")) {
			System.out.println("Genesis does not work with scalatest right now!");
			System.out.println("Cannot determine whether it uses JUnit 3 or 4!");
			throw new GenesisException("Unable to detect the test engine from the class path! Support JUnit3/4 only!");
		}
		if (testClassPath.contains("junit/4") || testClassPath.contains("junit-4") ||
			testClassPath.contains("junit-dep/4") || testClassPath.contains("junit-dep-4"))
			testRunnerClass = JUnit4Runner.class;
		else if (testClassPath.contains("junit/3") || testClassPath.contains("junit-3"))
			testRunnerClass = JUnit3Runner.class;
		else {
			System.out.println("Cannot determine whether it uses JUnit 3 or 4!");
			System.out.println("The class path:");
			System.out.println(testClassPath);
			throw new GenesisException("Unable to detect the test engine from the class path! Support JUnit3/4 only!");
		}
		this.workDir = workDir;
		this.p = null;
		this.startTime = 0;
		this.endTime = 0;
		this.cases = cases;
		this.classPath = testClassPath;
		this.exitCode = 0;
		this.out = null;
	}

	public void run() {
		try {
			ArrayList<String> cmds = new ArrayList<String>();
			cmds.add(Config.jvmCmd);
			cmds.add("-cp");
			String cp = testRunnerClass.getProtectionDomain().getCodeSource().getLocation().getPath().toString();
			cp += Config.classPathSep + classPath;
			cmds.add(cp);
			cmds.add(testRunnerClass.getName());
			for (Testcase c : cases)
				cmds.add(c.testClass + "#" +  c.testName);
			//String cmdStr = cmds.toString().replace("[", "").replace("]", "").replace(",", " ");
			//System.out.println("cmd str: " + cmdStr);
			ecmd = new ExecShellCmd(cmds.toArray(new String[cmds.size()]), workDir, true, false);
			p = ecmd.getProcess();
			startTime = System.currentTimeMillis();
		}
		catch (IllegalThreadStateException  e) {
			e.printStackTrace();
			throw new GenesisException("Testcase executor hits unexpected problems!");
		}
	}

	public HashMap<Testcase, TestResult> getResult() throws InterruptedException {
		TestWorkerThread w = new TestWorkerThread(p);
		w.start();
		try {
			w.join();
			endTime = System.currentTimeMillis();
			if (p.isAlive()) {
				p.destroy();
				exitCode = -1;
			}
			else
				exitCode = p.exitValue();
			//System.out.println("Exitcode: " + exitCode);			
			return processOutput();
		}
		finally {
			p.destroy();
		}
	}
	
	public HashMap<Testcase, TestResult> getResult(long waitTime) throws InterruptedException {
		TestWorkerThread w = new TestWorkerThread(p);
		w.start();
		try {
			System.out.println("[DEBUG] Run for: " + waitTime);
			w.join(waitTime);
			endTime = System.currentTimeMillis();
			if (p.isAlive()) {
				System.out.println("[DEBUG] Timeout! destroy!");
				p.destroy();
				exitCode = -1;
			}
			else
				exitCode = p.exitValue();
			return processOutput();
		}
		finally {
			p.destroy();
		}
	}

	private HashMap<Testcase, TestResult> processOutput() {
		HashMap<Testcase, TestResult> ret = new HashMap<Testcase, TestResult>();
		try {
			out = ecmd.getOutput();
			String[] lines = out.split("\n");
			StringBuffer caseOut = new StringBuffer();
			for (String line : lines) {
				if (line.startsWith(RunnerSEP)) {
					String remainingLine = line.substring(RunnerSEP.length()).trim();
					int idx1 = remainingLine.indexOf(' ');
					int idx2 = remainingLine.lastIndexOf(' ');
					String testClass = remainingLine.substring(0, idx1).trim();
					String testMethod = remainingLine.substring(idx1 + 1, idx2).trim();
					int status = Integer.parseInt(remainingLine.substring(idx2 + 1).trim());
					/*Scanner s = new Scanner(remainingLine);
					String testClass = s.next();
					String testMethod = s.next();
					int status = s.nextInt();
					s.close();*/
					Testcase curCase = new Testcase(testClass, testMethod);
					ret.put(curCase, new TestResult(status, caseOut.toString()));
					caseOut = new StringBuffer();
				}
				else {
					caseOut.append(line);
					caseOut.append("\n");
				}
			}
		}
		catch (InterruptedException e) {
			e.printStackTrace();
			throw new GenesisException("This should never happen!");
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
			System.out.println("This may happen if the test case timeout, just going to ignore!");
		}
		/*catch (IOException e) {
			e.printStackTrace();
			System.out.println(out);
			throw new GenesisException("Unable to parse the TestRunner's output!");
		}*/
		return ret;
	}
}
