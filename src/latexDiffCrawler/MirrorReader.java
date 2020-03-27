package latexDiffCrawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class MirrorReader extends Thread {

	private ArrayList<Mirror> mirrors;
	Hashtable<String, Long> table;
	private List<String> files;
	private List<String> checkedFilesList = new ArrayList<String>();
	private int checkedFilesForCurrentList = 0;
	private int checkedFiles;
	public boolean exit = false;
	private List<MirrorReader> mirrorReaders;
	public boolean waitingToHelp;

	public MirrorReader(ArrayList<Mirror> mirrors, List<String> files, Hashtable<String, Long> table) {
		this.mirrors = mirrors;
		this.files = files;
		this.table = table;
	}

	public List<String> getFiles() {
		return files;
	}

	public int getCheckedFiles() {
		return checkedFiles;
	}

	public int getCheckedFilesForCurrentList() {
		return checkedFilesForCurrentList;
	}

	public void setMirrorReaders(List<MirrorReader> readers) {
		this.mirrorReaders = readers;
	}

	public static String getFileName(String uri) {
		if (uri.contains("/"))
			return uri.substring(uri.lastIndexOf("/") + 1, uri.length());
		else
			return uri;
	}

	public static String makeDir(Mirror mirror, String file) {
		String mirrorFilePath = mirror.getDirectory() + "\\" + file;
		String pathToMake = "";
		if (file.contains("/")) {
			pathToMake = mirrorFilePath.substring(0, mirrorFilePath.lastIndexOf("/"));
			new File(pathToMake).mkdirs();
		}
		return pathToMake;
	}

	public static LocalDateTime getMasterTimeStamp() {
		InputStream is;
		try {
			is = Files.newInputStream(Paths.get(Constants.MASTER_DIR + "\\timestamp"));
			BufferedReader in = null;
			if (is != null) {
				in = new BufferedReader(new InputStreamReader(is));
				String inputLine = "";
				String timestamp = "";
				if (in != null) {
					while ((inputLine = in.readLine()) != null)
						timestamp = inputLine;
				}
				return Mirror.localDateTimeParser(timestamp);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static boolean compareTimeStamps(Mirror mirror) {
		boolean equalTimeStamps = false;
		mirror.setTimestamp();
		LocalDateTime mirrorTimestamp = mirror.getTimestamp();
		if (mirrorTimestamp != null) {
			LocalDateTime masterTimestamp = getMasterTimeStamp();
			if (masterTimestamp != null)
				equalTimeStamps = mirrorTimestamp.equals(masterTimestamp);
		}
		return equalTimeStamps;
	}

	private void copyFile(String fileName, String file, String saveDir) {
		int end_index = fileName.lastIndexOf('.');
		String fileType = "";
		if (end_index == -1) {
			end_index = fileName.length();
		} else
			fileType = fileName.substring(fileName.lastIndexOf('.'), fileName.length());
		File dest = new File(saveDir + "/" + fileName.substring(0, end_index) + "_master" + fileType);
		String source = Constants.MASTER_DIR + "\\" + file;
//		} else if (file.equals(MasterHashHelper.fontsGreekKdInstall)) {
//			source = Constants.MASTER_DIR + "\\" + file;
//		}
		try {
			Files.copy(Paths.get(source), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			if (Main.difficultFiles.contains(file)) {
				if (file.endsWith(".")) /* systems\mac\textures\information\FAQ.comp.text.tex. */
					/* support\qfig\qfig3ple. */
					file = (String) file.subSequence(0, file.length() - 1);
				else {
					File orgFile = new File(source);
					if (orgFile.exists() && orgFile.isDirectory())
						file = file + file.charAt(file.length() - 1);
					else
						file = HTTPDownloadUtility.replaceNotAllowedCharactersForURI(file);
				}
				source = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				try {
					Files.copy(Paths.get(source), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} else {
				e.printStackTrace();
			}

		}
	}

	public void compareFiles() {
		checkedFilesForCurrentList = 0;
		if (files != null) {
//			if (Main.compareFILES_byname(mirror)) {
			for (int i = 0; i < files.size(); i++) {
				String file = files.get(i);
				long masterChecksum = 0;
				if (table.get(file) != null)
					masterChecksum = table.get(file);
				for (Mirror mirror : mirrors) {
					if (!HTTPDownloadUtility.filesAreEqual(file, mirror, masterChecksum)) {
						String saveDir = MirrorReader.makeDir(mirror, file);
						boolean didDownload = HTTPDownloadUtility.downloadFile(mirror.getUrl() + file,
								mirror.getDirectory() + "\\" + file, false);
						if (!didDownload)
							// TODO: files.add(file);
							System.out.println(file + " has to be downloaded again");
						else { // Copy the master File
							String fileName = MirrorReader.getFileName(file);
							copyFile(fileName, file, saveDir);
						}
					}
				}
				checkedFiles++;
				checkedFilesForCurrentList++;
			}
		}
		checkedFilesList.addAll(files);
		helpOtherMirror();
	}

	public List<String> getHelp() {
		int index = (files.size() - checkedFilesForCurrentList) / 2;
		List<String> subFilesList = null;
		subFilesList = files.subList(checkedFilesForCurrentList + index, files.size());
		files = files.subList(0, checkedFilesForCurrentList + index);
		return subFilesList;
	}

	private void helpOtherMirror() {
		MirrorReader needsHelpReader = null;
		int i = 2;
		for (MirrorReader reader : mirrorReaders) {
			int stillTocheck = reader.getFiles().size() - reader.getCheckedFilesForCurrentList();
			if (stillTocheck >= i && !reader.equals(this)) {
				i = stillTocheck;
				needsHelpReader = reader;
//				System.out.println(needsHelpReader.toString() + " has still " + i + " elements"  );
			}

		}
		if (needsHelpReader != null) {
//			String oldStart = files.get(0);
//			int oldSize = files.size();
			List<String> subFiles = needsHelpReader.getHelp();
			if (subFiles != null) {
				files = subFiles;
//				System.out.println("\n I finished my List with" + oldSize + "elements, starting with " + oldStart);
//				System.out.println("Now I am helping at " + files.get(0) + "\n");
//				System.out.println("My list has now " + files.size() + " elements.");
				compareFiles();
			} else {

				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				helpOtherMirror();
			}
		}
	}

	public List<String> checkedFiles() {
		return this.checkedFilesList;
	}

	public void run() {
		compareFiles();
	}

	public void removeMirror(Mirror mirror) {
		// TODO Auto-generated method stub
	}
}
