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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Enumeration;

import junit.framework.TestFailure;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import junit.framework.TestResult;

public class JUnit3Runner {

	static class NullOutputStream extends OutputStream {
		@Override
		public void write(int arg0) throws IOException { }
	}
	
	public static void main(String args[]) {
		
		for (String arg : args) {
	        String[] classAndMethod = arg.split("#");
	        TestRunner r = new TestRunner(new PrintStream(new NullOutputStream()));
	        try {
	        	TestResult res = r.doRun(TestSuite.createTest(Class.forName(classAndMethod[0]), classAndMethod[1]));
	        	Enumeration<TestFailure> fs = res.failures();
	        	if (fs.hasMoreElements()) {
	        		TestFailure f = fs.nextElement();
                    f.thrownException().printStackTrace(System.out);
	        	}
	        	System.out.println(TestcaseExecutor.RunnerSEP + " " + classAndMethod[0] + " " + classAndMethod[1] + " " + (res.wasSuccessful() ? 0 : 1));
	        }
	        catch (ClassNotFoundException e) {
	        	System.out.println(TestcaseExecutor.RunnerSEP + " " + classAndMethod[0] + " " + classAndMethod[1] + " -1");
	        }
		}
	}
}
