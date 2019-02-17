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
package genesis.repair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;

import genesis.Config;
import genesis.GenesisException;
import genesis.infrastructure.AppManager;
import genesis.node.MyCtNode;
import genesis.repair.compiler.JavaXToolsCompiler;
import genesis.repair.localization.SuspiciousLocation;
import genesis.repair.validation.Testcase;
import genesis.repair.validation.TestingOracle;
import genesis.repair.validation.ValidationOracle;

public class WorkdirManager {

	public static final String TESTCASE_FILE = "testcase.log";
	public static final String ORIGSRC_DIR = "src0";
	public static final String WORKSRC_DIR = "src";
	public static final String LOCALIZATION_FILE = "localization.log";
	public static final String CONFIG_FILE = "genesis.conf";
	public static final String ARG_LOG_FILE = "arg.log";
	public static final String TESTINFO_FILE = "testinfo.log";
	
	public static final String SRCPATH_PROPERTY = "src";
	public static final String TESTCASE_PROPERTY = "testcase";
	public static final String LOCALIZATION_FILE_PROPERTY = "locfile";
	
	Properties config;
	AppManager app;
	String workDirPath;
	String origSrcPath;
	ArrayList<Testcase> positiveCases;
	ArrayList<Testcase> negativeCases;
	ArrayList<SuspiciousLocation> suspiciousLocs;
	
	private WorkdirManager() {
		config = null;
		app = null;
		workDirPath = null;
		positiveCases = null;
		negativeCases = null;
		suspiciousLocs = null;
	}
	
	public static WorkdirManager createWithExistingWorkdir(String workDir, boolean skipInit) {
		WorkdirManager m = new WorkdirManager();
		m.config = new Properties();
		try {
			m.config.load(new FileInputStream(workDir + "/" + CONFIG_FILE));
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to load config file: " + workDir + "/" + CONFIG_FILE);
			return null;
		}
		
		m.workDirPath = workDir;
		if (!Files.exists(Paths.get(workDir + "/" + ORIGSRC_DIR)) || !Files.exists(Paths.get(workDir + "/" + TESTCASE_FILE))) {
			System.out.println("Malformed workdir!");
			return null;
		}
		else
			m.origSrcPath = workDir + "/" + ORIGSRC_DIR;
		
		try {
			m.readTestcaseFile(workDir + "/" + TESTCASE_FILE);
			if (Files.exists(Paths.get(workDir + "/" + LOCALIZATION_FILE))) {
				m.readLocalizationFile(workDir + "/" + LOCALIZATION_FILE);
			}
			m.initialize(skipInit);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to initialize the work directory!");
			return null;
		}
		
		return m;
	}

	private void readLocalizationFile(String fname) throws IOException {
		suspiciousLocs = new ArrayList<SuspiciousLocation>();
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(fname));
		String line = reader.readLine();
		int n = Integer.parseInt(line.trim());
		for (int i = 0; i < n; i++) {
			line = reader.readLine();
			suspiciousLocs.add(SuspiciousLocation.createFromStrLine(line));
		}
		reader.close();
	}
	
	private void writeLocalizationFile(String fname) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
		writer.write(Integer.toString(suspiciousLocs.size()));
		writer.newLine();
		for (SuspiciousLocation loc : suspiciousLocs) {
			writer.write(loc.toString());
			writer.newLine();
		}
		writer.close();
	}

	private void readTestcaseFile(String fname) throws IOException {
		positiveCases = new ArrayList<Testcase>();
		negativeCases = new ArrayList<Testcase>();
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(fname));
		String line = null;
		while ((line = reader.readLine()) != null) {
			// Going to skip empty lines
			if (line.trim().isEmpty())
				continue;
			int idx1 = line.indexOf(" ");
			int idx2 = line.lastIndexOf(" ");
			String testClass = line.substring(0, idx1).trim();
			String testName = line.substring(idx1 + 1, idx2).trim();
			int status = Integer.parseInt(line.substring(idx2 + 1).trim());
			if (status == 0)
				positiveCases.add(new Testcase(testClass, testName));
			else
				negativeCases.add(new Testcase(testClass, testName));
		}
		reader.close();
	}

	public static WorkdirManager createWithConfigFile(String configFile, String workDir) {
		String configFileDir = (new File(configFile)).getParentFile().getAbsolutePath();
		
		WorkdirManager m = new WorkdirManager();
		m.config = new Properties();
		try {
			m.config.load(new FileInputStream(configFile));
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to load config file: " + configFile);
			return null;
		}
		
		m.workDirPath = workDir;
		try {
			Files.createDirectory(Paths.get(workDir));
			m.origSrcPath = m.workDirPath + "/" + ORIGSRC_DIR;
			String srcpath = m.config.getProperty(SRCPATH_PROPERTY);
			if (!srcpath.startsWith("/"))
				srcpath = configFileDir + "/" + srcpath;
			FileUtils.copyDirectory(new File(srcpath), new File(m.origSrcPath));
		}
		catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed when setup the work directory");
		}
		
		String testcaseFile = m.config.getProperty(TESTCASE_PROPERTY);
		if (!testcaseFile.startsWith("/"))
			testcaseFile = configFileDir + "/" + testcaseFile;
			
		try {
			m.readTestcaseFile(testcaseFile);
			Files.copy(Paths.get(testcaseFile), Paths.get(workDir + "/" + TESTCASE_FILE));
			Files.copy(Paths.get(configFile), Paths.get(workDir + "/" + CONFIG_FILE));
			
			String locFname = m.config.getProperty(LOCALIZATION_FILE_PROPERTY);
			if (locFname != null) {
				if (!locFname.startsWith("/"))
					locFname = configFileDir + "/" + locFname;
				Files.copy(Paths.get(locFname), Paths.get(workDir + "/" + LOCALIZATION_FILE));
				m.readLocalizationFile(locFname);
			}
			
			m.initialize(false);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to initialize the work directory!");
			return null;
		}
		
		return m;
	}

	private void initialize(boolean skipInit) throws IOException, GenesisException {
		if (!skipInit) {
			// We are going to remove the old one if we are not skipping initialization
			if (Files.exists(Paths.get(workDirPath + "/" + WORKSRC_DIR)))
				FileUtils.deleteDirectory(new File(workDirPath + "/" + WORKSRC_DIR));
		}
		// We are going to copy it from original source if we do not have it in the directory
		if (!Files.exists(Paths.get(workDirPath + "/" + WORKSRC_DIR))) 
			FileUtils.copyDirectory(new File(workDirPath + "/" + ORIGSRC_DIR), new File(workDirPath + "/" + WORKSRC_DIR));
		
		app = new AppManager(workDirPath + "/" + WORKSRC_DIR);
		String argFname = workDirPath + Config.filePathSep + ARG_LOG_FILE;
		if (skipInit && Files.exists(Paths.get(argFname)))
			app.initializeCompileSessionsWithFile(argFname);
		else
			app.initializeCompileSessions(argFname, true);
		String testinfoFname = workDirPath + Config.filePathSep + TESTINFO_FILE;
		if (skipInit && Files.exists(Paths.get(testinfoFname)))
			app.initializeTestSessionsWithFile(testinfoFname);
		else
			app.initializeTestSessions(testinfoFname, false);
		
		if (!skipInit) {
			ValidationOracle oracle = new TestingOracle(this);
			List<Testcase> res = oracle.runTestcases(negativeCases);
			boolean fail = false;
			if (!res.isEmpty()) {
				System.out.println("Unexpected pass of the cases:");
				for (Testcase c : res) {
					System.out.println(c.testClass + "#" + c.testName);
				}
				fail = true;
			}
			res = oracle.runTestcases(positiveCases);
			if (res.size() != positiveCases.size()) {
				System.out.println("Unexpected failure of the cases: ");
				for (Testcase c : positiveCases) {
					boolean found = false;
					for (Testcase c1 : res)
						if (c.testClass.equals(c1.testClass) && c.testName.equals(c1.testName)) {
							found = true;
							break;
						}
					if (!found)
						System.out.println(c.testClass + "#" + c.testName);
				}
				fail = true;
			}
			if (fail) {
				System.out.println("Fixes testcase log file before you rerun genesis!");
				throw new GenesisException("Test result does not match the test case log file!");
			}
		}
	}

	public boolean hasSuspiciousLocations() {
		return suspiciousLocs != null;
	}

	public List<SuspiciousLocation> getSuspiciousLocations() {
		return suspiciousLocs;
	}

	public void setSuspiciousLocations(List<SuspiciousLocation> stmts) {
		suspiciousLocs = new ArrayList<SuspiciousLocation>(stmts);
		try {
			Files.deleteIfExists(Paths.get(workDirPath + "/" + LOCALIZATION_FILE));
			writeLocalizationFile(workDirPath + "/" + LOCALIZATION_FILE);
		}
		catch (IOException e) {
			e.printStackTrace();
			new GenesisException("Unable to write down localization result to " + workDirPath + "/" + LOCALIZATION_FILE);
		}
	}

	public MyCtNode getRootASTNode(String srcPath) {
		return app.getCtNode(workDirPath + "/" + WORKSRC_DIR + "/" + srcPath, false);
	}

	private boolean compileJavaFileTo(String classpath, String sourcePath, String code, Path tempDirPath) throws IOException {
		String className = getQualifiedClassName(sourcePath, code);
		ArrayList<String> options = new ArrayList<String>();
		options.add("-cp");
		options.add(classpath);
		// XXX: Don't know why, but these are required to compile some applications
		options.add("-sourcepath");
		options.add("");
		JavaXToolsCompiler comp = new JavaXToolsCompiler();
		
		//System.out.println("className: " + className);
		//System.out.println("options: " + options);
		//System.out.println("CODE:");
		//System.out.println(code);
		
		Map<String, byte[]> bytecodes = comp.javaBytecodeFor(className, code, options);
		if (bytecodes == null) return false;
		
		// We may get multiple class files out because the file may have multiple public inner classes!
		for (String outClassName : bytecodes.keySet()) {
			String[] tokens = outClassName.split("\\.");
			String fname = tempDirPath.toString();
			for (int i = 0; i < tokens.length - 1; i++) 
				fname += Config.filePathSep + tokens[i];
			Files.createDirectories(Paths.get(fname));
			fname += Config.filePathSep + tokens[tokens.length - 1] + ".class";
			
			FileOutputStream fos = new FileOutputStream(fname);
			fos.write(bytecodes.get(outClassName));
			fos.close();
		}
		return true;
	}

	public boolean compileJavaSourceTo(String sourcePath, String code, Path tempDirPath) throws IOException {
		return compileJavaFileTo(app.getClasspath(workDirPath + "/" + WORKSRC_DIR + "/" + sourcePath, false), sourcePath, code, tempDirPath);
	}

	public boolean compileJavaTestTo(String sourcePath, String code, Path tempDirPath) throws IOException {
		return compileJavaFileTo(app.getTestClasspath(workDirPath + "/" + WORKSRC_DIR + "/" + sourcePath, false), sourcePath, code, tempDirPath);
	}

	private String getQualifiedClassName(String sourcePath, String newCodeStr) {
		String[] lines = newCodeStr.split("\n");
		// XXX: This might be problematic if there is a /* */ comment together with package line,
		// but I really do not think any stupid programmer will do that
		String firstLine = null;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (!line.startsWith("/") && !line.startsWith("*") && !line.isEmpty()) {
				firstLine = line;
				break;
			}
		}
		String packagePath = null;
		if (firstLine != null && firstLine.startsWith("package")) {
			String str1 = firstLine.substring(8).trim();
			// The last char will be ";"
			packagePath = str1.substring(0, str1.length() - 1) + ".";
		}
		else {
			packagePath = "";
		}
		int idx = sourcePath.lastIndexOf(Config.filePathSep);
		String fname = sourcePath.substring(idx + 1);
		idx = fname.indexOf(".");
		if (idx == -1)
			return packagePath + fname;
		else
			return packagePath + fname.substring(0, idx);	
	}

	public List<Testcase> getPositiveCases() {
		return positiveCases;
	}
	
	public List<Testcase> getNegativeCases() {
		return negativeCases;
	}

	public int getTestSessionId(String testClass) {
		return app.getTestSessionId(testClass);
	}

	public String getTestSessionClasspath(Integer id) {
		return app.getTestSessionClasspath(id);
	}

	public void destroyWorkdir() {
		try {
			FileUtils.deleteDirectory(new File(workDirPath));
		}
		catch (IOException e) { }
	}

	public String getWorkSrcDir() {
		return workDirPath + Config.filePathSep + WORKSRC_DIR;
	}

	public AppManager getApp() {
		return app;
	}
}
