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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ExecShellCmd {

	Process p;
	
	class StreamConsumer extends Thread {
		InputStream is;
		StringBuffer res;
		boolean keep;
		
		StreamConsumer(InputStream is) {
			this(is, true);
		}
		
		StreamConsumer(InputStream is, boolean keep) {
			this.res = new StringBuffer();
			this.is = is;
			this.keep = keep;
		}
		
		public void run() {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = "";
			try {
				while ((line = reader.readLine()) != null) {
					if (keep)
						res.append(line + "\n");
					//System.out.println(line);
				}
				is.close();
			}
			catch (IOException e) {
				res.append("\n");
				res.append(e.getMessage() + "\n");
			}
		}
		
		public String getResult() {
			if (!keep)
				return null;
			else
				return res.toString();
		}
	}
	
	StreamConsumer out, err;
	
	public ExecShellCmd(String cmd, boolean keepout, boolean keeperr) {
		try {
			p = Runtime.getRuntime().exec(cmd);
			//p.getOutputStream().close();
		}
		catch (Exception e) {
			e.printStackTrace();
			p = null;
		}
		out = new StreamConsumer(p.getInputStream(), keepout);
		err = new StreamConsumer(p.getErrorStream(), keeperr);
		out.start();
		err.start();
	}
	
	public ExecShellCmd(String cmd, String wdir, boolean keepout, boolean keeperr) {
		File fwdir = new File(wdir);
		if (fwdir == null || !fwdir.isDirectory()) {
			p = null;
			return;
		}
		try {
			p = Runtime.getRuntime().exec(cmd, new String[0], fwdir);
			//p.getOutputStream().close();
		}
		catch (Exception e) {
			e.printStackTrace();
			p = null;
			return;
		}
		out = new StreamConsumer(p.getInputStream(), keepout);
		err = new StreamConsumer(p.getErrorStream(), keeperr);
		out.start();
		err.start();
	}
	
	public ExecShellCmd(String[] cmds, String wdir, boolean keepout, boolean keeperr) {
		File fwdir = new File(wdir);
		if (fwdir == null || !fwdir.isDirectory()) {
			p = null;
			return;
		}
		ProcessBuilder pb = new ProcessBuilder(cmds);
		pb.redirectInput();
		pb.directory(fwdir);
		pb.redirectErrorStream(false);
		try {
			p = pb.start();
		}
		catch (IOException e) {
			e.printStackTrace();
			p = null;
			return;
		}
		out = new StreamConsumer(p.getInputStream(), keepout);
		err = new StreamConsumer(p.getErrorStream(), keeperr);
		out.start();
		err.start();
	}
	
	public int waitExit() {
		if (p == null) return -1;
		boolean done = false;
		int ret = -1;
		do {
			try {
				ret = p.waitFor();
				done = true;
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		while (!done);
		return ret;
	}
	
	public Process getProcess() {
		return p;
	}
	
	public String getOutput() throws InterruptedException {
		out.join();
		return out.getResult(); 
	}
	
	public String getErr() throws InterruptedException {
		err.join();
		return err.getResult();
	}
	
	public static int runCmd(String cmd, boolean out, boolean err) {
		ExecShellCmd c = new ExecShellCmd(cmd, out, err);
		int ret = c.waitExit();
		try {
			if (out)
				System.out.println(c.getOutput());
			if (err)
				System.out.println(c.getErr());
		}
		catch (InterruptedException e) {
			// This should never happen
			// because it already exited!
			assert(false);
		}
		return ret;
	}
	
	public static int runCmd(String cmd, String wdir, boolean out, boolean err) {
		ExecShellCmd c = new ExecShellCmd(cmd, wdir, out, err);
		int ret = c.waitExit();
		try {
			if (out)
				System.out.println(c.getOutput());
			if (err)
				System.out.println(c.getErr());
		}
		catch (InterruptedException e) {
			// This should never happen
			// because it already exited!
			assert(false);
		}
		return ret;
	}
	
	public static int runCmd(String cmd) {
		return runCmd(cmd, false, false);
	}
	
	public static int runCmd(String cmd, String wdir) {
		return runCmd(cmd, wdir, false, false);
	}
}
