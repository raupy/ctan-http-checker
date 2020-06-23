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

/*
 * This class implements the whole comparing functionality and compares all 
 * the files that are listed in the @files list. 
 * It is implemented as a thread so that the comparing process can be parallel.
 * When a MirrorReader thread has finished all his files, it tries to find another
 * MirrorReader thread that still is comparing and helps this thread.  
 */
public class MirrorReader extends Thread {

	private ArrayList<Mirror> mirrors;
	Hashtable<String, Long> table;
	private List<String> files;
	private int checkedFiles = 0;
	private List<Mirror> outOfSyncMirrors = new ArrayList<Mirror>();
	private boolean exit;

	// attributes to work with other MirrorReaders
	private int checkedFilesForCurrentList = 0;
	private List<MirrorReader> mirrorReaders;

	public MirrorReader(ArrayList<Mirror> mirrors, List<String> files, Hashtable<String, Long> table) {
		this.mirrors = mirrors;
		this.files = files;
		this.table = table;
	}

	// returns the @mirror from the @mirrors list that has the same name as in @name
	public Mirror getMirrorByName(String name) {
		for (Mirror mirror : mirrors) {
			if (mirror.getName().equals(name))
				return mirror;
		}
		return null;
	}

	public ArrayList<Mirror> getMirrors() {
		return mirrors;
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

	public List<Mirror> getOutOfSyncMirrors() {
		return outOfSyncMirrors;
	}

	public void run() {
		compareFiles();
	}

	/*
	 * Iterates through its @files list and compares every file with the
	 * corresponding master file. After that it compares some single files from
	 * the @compareAgain list again if there was some trouble comparing them before.
	 * After that it helps another thread that isn't finished yet.
	 */
	public void compareFiles() {
		checkedFilesForCurrentList = 0;
		if (files != null) {
			List<String> compareAgain = new ArrayList<String>();
			for (int i = 0; i < files.size() && !exit; i++) {
				String file = files.get(i);
				compareSpecificFile(file, compareAgain);
				checkedFiles++;
				checkedFilesForCurrentList++;
			}
			if (!compareAgain.isEmpty())
				compareFilesAgain(compareAgain);
		}
		if (!exit)
			helpOtherThread();
	}

	/*
	 * Reads the @masterChecksum for a specific @file and compares it with the
	 * corresponding files that are uploaded at the @mirrors. Adds the @file,
	 * the @mirror and the @masterChecksum to the @compareAgain list if there was
	 * some trouble comparing them.
	 */
	public void compareSpecificFile(String file, List<String> compareAgain) {
		long masterChecksum = 0;
		if (table.get(file) != null)
			masterChecksum = table.get(file);
		for (int i = 0; i < mirrors.size(); i++) {
			Mirror mirror = mirrors.get(i);
			if (outOfSyncMirrors.contains(mirror))
				continue;
			if (!compareSpecificFileForSpecificMirror(file, mirror, masterChecksum))
				compareAgain.add(mirror.getName() + " ,,, " + file + " ,,, " + masterChecksum);
		}
	}

	/*
	 * Compares the @file that is uploaded at the specific @mirror with
	 * the @masterChecksum. If they are not equal it tries to download it. If the
	 * download was successful it copies the file from the master directory to the
	 * mirror directory. Otherwise the files have to be compared later again.
	 * Returns true if the files don't have to be compared again.
	 */
	public boolean compareSpecificFileForSpecificMirror(String file, Mirror mirror, long masterChecksum) {
		boolean compareNotAgain = HTTPDownloadUtility.filesAreEqual(file, mirror, masterChecksum, true);
		if (!compareNotAgain) {
			compareNotAgain = HTTPDownloadUtility.filesAreEqual(file, mirror, masterChecksum, false);
			if (!compareNotAgain) {
				String saveDir = MirrorReader.makeDir(mirror, file);
				boolean didDownload = HTTPDownloadUtility.downloadFile(mirror.getUrl() + file,
						mirror.getDirectory() + "\\" + file, false);
				if (didDownload) {
					compareNotAgain = true;
					if (HTTPDownloadUtility.compareHashesForLocalFiles(Constants.MASTER_DIR + "\\" + file,
							mirror.getDirectory() + "\\" + file)) {
						File delete = new File(mirror.getDirectory() + "\\" + file);
						delete.delete();
					} else {
						// Copy the master File
						String fileName = MirrorReader.getFileName(file);
						copyFile(fileName, file, saveDir);
					}
				}
			}

		}
		return compareNotAgain;
	}

	/*
	 * A bit ugly method that normally just has to copy the @file from the master
	 * directory to the @saveDir directory. The problem is, that it has to cope with
	 * some very ugly called files that contain e.g. characters like a ':' or a '.'
	 * in the end. These files are listed in the difficultFiles.txt file and saved
	 * in a seperate special directory. The whole part after the IOException line
	 * deals with those special files.
	 */
	private void copyFile(String fileName, String file, String saveDir) {
		int end_index = fileName.lastIndexOf('.');
		String fileType = "";
		if (end_index == -1) {
			end_index = fileName.length();
		} else
			fileType = fileName.substring(fileName.lastIndexOf('.'), fileName.length());
		File dest = new File(saveDir + "/" + fileName.substring(0, end_index) + "_master" + fileType);
		String source = Constants.MASTER_DIR + "\\" + file;
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

	/*
	 * Compares the files that caused earlier some trouble again. Doesn't do
	 * anything if there is again trouble, just moves on to the next file.
	 */
	public void compareFilesAgain(List<String> compareAgain) {
		for (String s : compareAgain) {
			String[] split = s.split(" ,,, ");
			Mirror mirror = this.getMirrorByName(split[0]);
			if (mirror != null) {
				compareSpecificFileForSpecificMirror(split[1], mirror, Long.parseLong(split[2]));
			}
		}
	}

	/*
	 * Returns the last part of an @uri, i.e. the name of the file that is uploaded
	 * there.
	 */
	public static String getFileName(String uri) {
		if (uri.contains("/"))
			return uri.substring(uri.lastIndexOf("/") + 1, uri.length());
		else
			return uri;
	}

	/*
	 * Makes a directory for the specific @file in the @mirror directory. Returns
	 * the path to this directory.
	 */
	public static String makeDir(Mirror mirror, String file) {
		String mirrorFilePath = mirror.getDirectory() + "\\" + file;
		String pathToMake = "";
		if (file.contains("/")) {
			pathToMake = mirrorFilePath.substring(0, mirrorFilePath.lastIndexOf("/"));
			new File(pathToMake).mkdirs();
		}
		return pathToMake;
	}

	/*
	 * Returns the current local master time stamp from the timestamp file or null
	 * if there was an IOException.
	 */
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

	/*
	 * Returns true if the @mirror's and the master's time stamps are equal, false
	 * if otherwise.
	 */
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

	/*
	 * Is called from another MirrorReader that is already finished and wants to
	 * help this instance. Divides the @files list in two equally big sublists and
	 * returns the sublist for the helping MirrorReader.
	 */
	public List<String> getHelp() {
		int index = (files.size() - checkedFilesForCurrentList) / 2;
		List<String> subFilesList = null;
		subFilesList = files.subList(checkedFilesForCurrentList + index, files.size());
		files = files.subList(0, checkedFilesForCurrentList + index);
		return subFilesList;
	}

	/*
	 * Looks for the MirrorReader that has currently the most files left that have
	 * to be compared. Tries then to help. If this didn't work, it sleeps 30 seconds
	 * and starts looking again. If no one needs help anymore, the method returns
	 * and the MirrorReader thread is terminated.
	 */
	private void helpOtherThread() {
		MirrorReader needsHelpReader = null;
		int i = 2;
		for (int j = 0; j < mirrorReaders.size(); j++) {
			MirrorReader reader = mirrorReaders.get(j);
			int stillTocheck = 0;
			if (reader.getFiles() != null)
				stillTocheck = reader.getFiles().size() - reader.getCheckedFilesForCurrentList();
			if (stillTocheck >= i && !reader.equals(this)) {
				i = stillTocheck;
				needsHelpReader = reader;
			}

		}
		if (needsHelpReader != null) {
			List<String> subFiles = needsHelpReader.getHelp();
			if (subFiles != null) {
				files = subFiles;
				compareFiles();
			} else {
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				helpOtherThread();
			}
		}
	}

	// removes a @mirror from the @mirrors list
	// is called from the TimeStampThread that looks for updates for the specific
	// @mirror
	public void removeMirror(Mirror mirror) {
		outOfSyncMirrors.add(mirror);
		if (outOfSyncMirrors.size() == mirrors.size()) {
			exit = true;
		}
	}
}
