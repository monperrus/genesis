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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class JUnit4Runner {
	
	public static void main(String args[]) throws IOException {
		// Put stdout together with stderr, this will help server-client case
		System.setErr(System.out);
		//Random r = new Random();
		//BufferedWriter w = new BufferedWriter(new FileWriter("/tmp/junit4run" + r.nextInt(1000) + ".log"));
		//w.write("Args: " + args);
		//w.newLine();
		for (String arg : args) {
			//w.write("Processing: " + arg);
			//w.newLine();
	        String[] classAndMethod = arg.split("#");
	        try {
	        	Request request = Request.method(Class.forName(classAndMethod[0]),
	        			classAndMethod[1]); 
		        Result result = new JUnitCore().run(request);
		        if (result.getFailureCount() == 1) {
		        	Failure f = result.getFailures().get(0);
                    f.getException().printStackTrace(System.out);
		        	//w.write(f.getTrace());
		        	//w.newLine();
		        	//w.write("FAILED!\n");
		        }
		        else {
		        	//w.write("SUCC!\n");
		        }
		        System.out.println(TestcaseExecutor.RunnerSEP + " " + classAndMethod[0] + " " + classAndMethod[1] + " " + (result.wasSuccessful() ? 0 : 1));
	        }
	        catch (ClassNotFoundException e) {
	        	//w.write("ERROR!\n" + e.getMessage() + "\n");
	        	System.out.println(TestcaseExecutor.RunnerSEP + " " + classAndMethod[0] + " " + classAndMethod[1] + " -1");
	        }
	        //w.flush();
		}
		//w.close();
	}
}
