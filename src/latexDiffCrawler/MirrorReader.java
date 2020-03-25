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
	private int bigSize;

	public MirrorReader(ArrayList<Mirror> mirrors, List<String> files, Hashtable<String, Long> table, int bigSize) {
		this.mirrors = mirrors;
		this.files = files;
		this.bigSize = bigSize;
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

	public void compareFiles() {
		checkedFilesForCurrentList = 0;
		if (files != null) {
//			if (Main.compareFILES_byname(mirror)) {
			for (int i = 0; i < files.size(); i++) {
				String file = files.get(i);
				System.out.println(file + " : this is nr. " + (i + 1));
				long masterChecksum = table.get(file);
				for (Mirror mirror : mirrors) {
					if (!HTTPDownloadUtility.filesAreEqual(file, mirror, masterChecksum)) {
						String saveDir = MirrorReader.makeDir(mirror, file);
						boolean didDownload = HTTPDownloadUtility.downloadFile(mirror.getUrl() + file,
								mirror.getDirectory() + "\\" + file);
						if (!didDownload)
							// TODO: files.add(file);
							System.out.println(file + " has to be downloaded again");
						else {
							// Copy the master File
							String fileName = MirrorReader.getFileName(file);
							if(file.equals(MasterHashHelper.fontsGreekKdInstall)) {
								file = MasterHashHelper.workAround;
							}
							File dest = new File(saveDir + "/" + fileName.substring(0, fileName.lastIndexOf('.'))
									+ "_master" + fileName.substring(fileName.lastIndexOf('.'), fileName.length()));
							try {
								Files.copy(Paths.get(Constants.MASTER_DIR + "\\" + file), dest.toPath(),
										StandardCopyOption.REPLACE_EXISTING);
							} catch (IOException e) {
								e.printStackTrace();
							}
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
		if (index > 10) {
			subFilesList = files.subList(checkedFilesForCurrentList + index, files.size());
			files = files.subList(0, checkedFilesForCurrentList + index);
		}
		return subFilesList;
	}

	private void helpOtherMirror() {
		MirrorReader needsHelpReader = null;
		int i = bigSize;
		int bigChecked = 0;
		for (MirrorReader reader : mirrorReaders) {
			bigChecked += reader.getCheckedFiles();
			int checked = reader.getCheckedFilesForCurrentList();
			if (checked < i && !reader.equals(this)) {
				i = checked;
				needsHelpReader = reader;
			}
		}
		if (needsHelpReader != null && bigChecked < 0.95 * bigSize) {
			String oldStart = files.get(0);
			List<String> subFiles = needsHelpReader.getHelp();
			files = subFiles;
			System.out.println("\n I finished my List starting with " + oldStart);
			if (files != null) {
				System.out.println("Now I am helping at " + files.get(0) + "\n");
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
		else System.out.println("No one needs help anymore, I am finished.");
	}
	
	public List<String> checkedFiles(){
		return this.checkedFilesList;
	}
	
	private void helpOtherMirrorNew() {
		MirrorReader needsHelpReader = null;
		int i = bigSize;
		int bigChecked = 0;
		for (MirrorReader reader : mirrorReaders) {
			bigChecked += reader.getCheckedFiles();
			int checked = reader.getCheckedFilesForCurrentList();
			if (checked < i && !reader.equals(this)) {
				i = checked;
				needsHelpReader = reader;
			}
		}
		if (needsHelpReader != null && bigChecked < 0.95 * bigSize) {
			String oldStart = "";
			for(String file : table.keySet()) {
				oldStart = file;
				break;
			}
			files = needsHelpReader.getHelp();
			System.out.println("\n I finished my List starting with " + oldStart);
			if (files != null) {
				System.out.println("Now I am helping at " + files.get(0) + "\n");
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
		else System.out.println("No one needs help anymore, I am finished.");
	}

	public void run() {
		compareFiles();
	}

	public void removeMirror(Mirror mirror) {
		// TODO Auto-generated method stub
	}
}
