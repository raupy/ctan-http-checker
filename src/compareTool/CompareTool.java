package compareTool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CompareTool {

	/*
	 * Makes file lists for every mirror in the directory @path. Produces then a
	 * batch file for each of those mirrors that can make the file reports with
	 * Beyond Compare.
	 */
	public void makeScripts(String path) throws IOException {
		List<String> mirrors = mirrorPaths(path + "\\directories.txt");
		for (String mirror : mirrors) {
			String mirrorPath = path + "\\" + mirror;
			writeFileListBat(mirrorPath, "dir /b /s > fileslist.txt");
			runListBat(mirrorPath);
			makeScriptsForMirror(mirrorPath);
		}
	}

	/*
	 * Returns a List of Strings, each element is a mirror directory that is a part
	 * of the directory @path (e.g. C:/...blabla.../.../.../mirrors)
	 */
	private List<String> mirrorPaths(String path) throws IOException {
		List<String> entries = readFile(path);
		entries.remove("directories.txt");
		entries.remove("makeList.bat");
		entries.remove("");
		return entries;
	}

	/*
	 * Returns a List of Strings, each element is a line from the @file
	 */
	private List<String> readFile(String file) throws IOException {
		InputStream is = Files.newInputStream(Paths.get(file));
		BufferedReader in = null;
		ArrayList<String> lines = null;
		if (is != null) {
			in = new BufferedReader(new InputStreamReader(is));
			String inputLine = "";
			if (in != null) {
				lines = new ArrayList<String>();
				while ((inputLine = in.readLine()) != null) {
					lines.add(inputLine);
				}
			}
		}
		return lines;
	}

	/*
	 * Makes a Batch File
	 */
	public void writeFileListBat(String path, String command) throws IOException {
		writeThis(command, path + "\\makeList.bat");
	}

	/*
	 * Writes the String @writeThis in a file with the path @path.
	 */
	private void writeThis(String writeThis, String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		writer.write(writeThis);
		writer.close();
	}

	/*
	 * Runs the batch file "makeList.bat" in the specific directory @path
	 */
	public void runListBat(String path) throws IOException {
		String[] command = { "cmd", "/c", "makeList.bat" };
		Runtime rn = Runtime.getRuntime();
		Process p = rn.exec(command, null, new File(path));
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Makes scripts for a specific @mirrorPath with the files from "fileslist.txt".
	 * Also makes a batch file that uses those scripts
	 */
	private void makeScriptsForMirror(String mirrorPath) throws IOException {
		String file = "fileslist.txt";
		writeScripts(mirrorPath, makeListsForMasterAndMirrorFiles(readFile(mirrorPath + "\\" + file)));
	}

	/*
	 * Returns a List of two ArrayLists from a single @list that contains the files
	 * in a mirror directory. The ArrayLists contain the file names for the master
	 * and mirror files respectively. The first list is the master list, the second
	 * the mirror list. The same index i of both lists describes the same file, e.g.
	 * xxx_master.txt and xxx.txt
	 */
	private List<ArrayList<String>> makeListsForMasterAndMirrorFiles(List<String> list) {
		List<ArrayList<String>> twoLists = new ArrayList<ArrayList<String>>();
		ArrayList<String> mirr = new ArrayList<String>();
		ArrayList<String> mast = new ArrayList<String>();
		for (String file : list) {
			File f = new File(file);
			if (f.exists() && !f.isDirectory() && !file.contains("fileslist.txt")
					&& !file.contains("makeList.bat")) {
				if (file.contains("_master")) {
					mast.add(file);
					if (file.contains("#disk_master.00"))
						mirr.add(file.replace("#disk_master.00", "#disk.00"));
				}

				else {
					if (file.contains("#disk.00") && !file.contains(".dir"))
						continue;
					mirr.add(file);
				}

			}
		}
		twoLists.add(mast);
		twoLists.add(mirr);
		return twoLists;
	}

	/*
	 * Writes all the Beyond Compare scripts for that @mirrorPath directory (in the
	 * for-loop). After that, calls the writeReportsBat function to make a batch
	 * file that calls all these Beyond Compare commands
	 */
	private void writeScripts(String mirrorPath, List<ArrayList<String>> lists) throws IOException {
		ArrayList<String> mast = lists.get(0);
		ArrayList<String> mirr = lists.get(1);
		String beginning = "text-report layout:side-by-side options:display-mismatches output-to:printer output-options:print-color,wrap-word ";
		int i;
		for (i = 0; i < mirr.size() && i < mast.size(); i++) {
			String write = beginning + "\"" + mast.get(i) + "\" \"" + mirr.get(i) + "\"";
			String path = mirrorPath + "\\" + "beyondScript" + i + ".txt";
			writeThis(write, path);
		}
		writeReportsBat(mirrorPath, i - 1);
	}

	/*
	 * Writes a batch file for that @mirrorPath directory, that will execute all
	 * the @scriptsCount Beyond Compare scripts
	 */
	private void writeReportsBat(String mirrorPath, int scriptsCount) throws IOException {
		String one = "cd C:\\Program Files\\Beyond Compare 4\n";
		String two = "for /l %%x in (0, 1, " + scriptsCount + ") do (\n";
		String three = "   BCompare.exe @" + mirrorPath + "\\beyondScript%%x.txt\n";
		String four = ")\n";
		String five = "PAUSE";
		String writeThis = one + two + three + four + five;
		String path = mirrorPath + "\\" + "makeReports.bat";
		writeThis(writeThis, path);
	}

	public static void main(String[] args) {
		String mirrorDir = "C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\repo\\mirrors";
		CompareTool compareTool = new CompareTool();
		try {
			compareTool.writeFileListBat(mirrorDir, "dir /b > directories.txt");
			compareTool.runListBat(mirrorDir);
			compareTool.makeScripts(mirrorDir);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
