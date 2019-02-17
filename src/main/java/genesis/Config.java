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
package genesis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
	public static final String pythonSourceDir;
	public static final String transformSourceDir;
	public static final long costlimit; 
	public static final long sizeCap;
	public static final int enumGenEleBound;
	public static final String DBPath;
	public static final String tmpargfile;
	public static final String tmpTestinfoFile;
	public static final boolean collapseTextMsg;
	public static final boolean presetConstants;
	public static final boolean enableVarContains;
	public static final boolean filterStringConstant;
	public static final String textMsgToken; 
	public static final String newVarToken;
	public static final String tmpDirectory;
	public static final String tmpDirPrefix;
	public static final String jvmCmd;
	public static final String classPathSep;
	public static final String filePathSep;

	public static final long perCaseTimeout;
	
	public static final int cacheSize;
	public static final int concurrencyLevel;
	
	public static final boolean stackTraceLocalization_removeTry;
	public static final int stackTraceLocalization_nLocs;
	public static final int stackTraceLocalization_nSurroundingLines;
	public static final double stackTraceLocalization_lineDistanceWeight;
	
	static {
		String confname = System.getenv("GENESIS_CONF");
		Properties p = new Properties();
		File f = null;
		if (confname != null) 
			f = new File(confname);
		else
			f = new File("./global.conf");
		
		if (f.exists() && f.isFile()) {
			try {
				p.load(new FileInputStream(f));
			}
			catch (IOException e) {
				e.printStackTrace();
				System.out.println("Unable to read global config file: " + f.getName() + ", setup GENESIS_CONF environment or " +
						" put a global.conf file in the current directory. See global-example.conf for reference.");
				System.exit(1);
			}
		}
		else {
			System.out.println("Unable to read global config file: " + f.getName() + ", setup GENESIS_CONF environment or " +
					" put a global.conf file in the current directory. See global-example.conf for reference.");
			System.exit(1);
		}
		costlimit = Long.parseLong(getPropertyOrThrowError(p, "cost_limit"));
		pythonSourceDir = getPropertyOrThrowError(p, "python_src_dir");
		transformSourceDir = getPropertyOrThrowError(p, "transform_src_dir");
		sizeCap = Long.parseLong(getPropertyOrThrowError(p, "search_space_cap"));
		enumGenEleBound = Integer.parseInt(getPropertyOrThrowError(p, "enumerate_generator_element_bound"));
		DBPath = getPropertyOrThrowError(p, "db_path");
		tmpargfile = p.getProperty("tmp_arg_file", "/tmp/args.log");
		tmpTestinfoFile = p.getProperty("tmp_testinfo_file", "/tmp/testinfo.log");
		collapseTextMsg = Boolean.parseBoolean(p.getProperty("collapse_text_message", "true"));
		presetConstants = Boolean.parseBoolean(p.getProperty("preset_constants", "false"));
		enableVarContains = Boolean.parseBoolean(p.getProperty("enable_var_contains", "false"));
		// XXX: This has to be turned off false to pass CorpusTest
		filterStringConstant = Boolean.parseBoolean(p.getProperty("filter_string_constant", "false"));
		textMsgToken = p.getProperty("text_message_token", "__TEXTMSG");
		newVarToken = p.getProperty("new_var_token", "__new_var");
		tmpDirectory = p.getProperty("tmp_dir", "/tmp");
		tmpDirPrefix = p.getProperty("genesis_tmp_dir_prefix", "__genesis");
		jvmCmd = getPropertyOrThrowError(p, "jvm_cmd");
		classPathSep = p.getProperty("class_path_separator", ":");
		filePathSep = p.getProperty("file_path_separator", "/");

		perCaseTimeout = Long.parseLong(p.getProperty("testcase_timeout", "0"));
		concurrencyLevel = Integer.parseInt(p.getProperty("concurrency_level", Integer.toString(Runtime.getRuntime().availableProcessors())));
		cacheSize = Integer.parseInt(p.getProperty("cache_size", "100000"));
		
		stackTraceLocalization_removeTry = Boolean.parseBoolean(p.getProperty("stack_trace_localization_remove_try", "true"));
		stackTraceLocalization_nLocs = Integer.parseInt(getPropertyOrThrowError(p, "stack_trace_localization_n_locs"));
		stackTraceLocalization_nSurroundingLines = Integer.parseInt(getPropertyOrThrowError(p, "stack_trace_localization_n_surrounding_lines"));
		stackTraceLocalization_lineDistanceWeight = Double.parseDouble(getPropertyOrThrowError(p, "stack_trace_localization_line_distance_weight"));
	}

	private static String getPropertyOrThrowError(Properties p, String name) {
		String ret = p.getProperty(name);
		if (ret == null)
			throw new GenesisException("Invalid global config file, no " + name + " property!");
		return ret;
	}
}
