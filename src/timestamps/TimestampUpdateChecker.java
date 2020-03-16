package timestamps;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.*;
import latexDiffCrawler.Constants;
import latexDiffCrawler.Mirror;

public class TimestampUpdateChecker {

	static ArrayList<latexDiffCrawler.Mirror> mirrors = new ArrayList<latexDiffCrawler.Mirror>();
	static String lastPath = "";
	

	/*
	 * returns the time of the last update of this mirror that you can find under
	 * url/timestamp example for what the file looks like: # This file is for
	 * administrative purposes only. # The source CTAN of this site's material:
	 * dante.ctan.org # The year-month-day-hour-minute of this site's material:
	 * 2020-02-05-06-02 so this method just returns the last line of the file
	 */
	public static String getTimestamp(String url) {
		URL ctanMirror = null;
		try {
			ctanMirror = new URL(url);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		BufferedReader in = null;
		String timestamp = "no connection";
		if (ctanMirror != null) {
			try {
				in = new BufferedReader(new InputStreamReader(ctanMirror.openStream()));
			} catch (IOException e) {
				e.printStackTrace();
			}
			String inputLine = "";

			if (in != null) {
				try {
					while ((inputLine = in.readLine()) != null)
						timestamp = inputLine;
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return timestamp;

	}

	/*
	 * Initialises and returns the ArrayList of CTAN mirrors with the entries that
	 * are listed in the .txt file
	 */
	public static ArrayList<String> initCtanArray(String fileWithCtanMirrors) throws IOException {
		ArrayList<String> ctanMirrors = new ArrayList<String>();
		BufferedReader in = new BufferedReader(new FileReader(fileWithCtanMirrors));
		String inputLine = "";
		while ((inputLine = in.readLine()) != null)
			ctanMirrors.add(inputLine);
		in.close();
		return ctanMirrors;
	}

	/*
	 * checks if any mirror was updated since the last check if yes, this timestamp
	 * will be added to the mirror timestamp history returns an ArrayList of
	 * ArrayLists of Strings that contains this history for every mirror
	 */
	public static ArrayList<ArrayList<String>> checkForUpdates(ArrayList<String> ctanMirrors,
			ArrayList<ArrayList<String>> updateList) {
		for (int i = 0; i < ctanMirrors.size(); i++) {
			String ctanMirror = ctanMirrors.get(i);
			String timestamp = getTimestamp(ctanMirror + "timestamp");
			ArrayList<String> mirrorI = updateList.get(i);
			int size = mirrorI.size();
			String lastUpdate = mirrorI.get(size - 1);
			if (!timestamp.equals(lastUpdate))
				mirrorI.add(timestamp);
		}
		return updateList;
	}

	public static ArrayList<ArrayList<String>> checkForUpdates2(ArrayList<String> ctanMirrors,
			ArrayList<ArrayList<String>> updateList) {
		for (int i = 0; i < ctanMirrors.size(); i++) {
			Mirror ctanMirror = mirrors.get(i);
			ctanMirror.setTimestamp();
			String timestamp = "no connection";
			if (ctanMirror.getTimestamp() != null)
				timestamp = ctanMirror.getTimestamp().toString();
			ArrayList<String> mirrorI = updateList.get(i);
			int size = mirrorI.size();
			String lastUpdate = mirrorI.get(size - 1);
			if (!timestamp.equals(lastUpdate)) {
				mirrorI.add("timestamp changed at " + LocalDateTime.now().toString());
				mirrorI.add(timestamp);

			}
		}
		return updateList;
	}

	/*
	 * writes the result that is saved in updateList to a file version is just a
	 * safety thing for redundancy so that maybe if something crashes over night or
	 * because of power loss or some thing not all results are lost
	 */
	public static void writeToFile(ArrayList<ArrayList<String>> updateList, String version) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(
					"C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\src\\UpdateFile" + version + ".txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			Iterator<ArrayList<String>> bigIt = updateList.iterator();
			while (bigIt.hasNext()) {
				Iterator<String> it = bigIt.next().iterator();
				while (it.hasNext()) {
					String update = it.next();
					writer.write(update + ", ");
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeToFile2(ArrayList<ArrayList<String>> updateList, String version) {
		BufferedWriter writer = null;
		String path = "C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\UpdateFiles\\UpdateFile" + version + ".txt";
		try {
			writer = new BufferedWriter(new FileWriter(path));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			Iterator<ArrayList<String>> bigIt = updateList.iterator();
			while (bigIt.hasNext()) {
				Iterator<String> it = bigIt.next().iterator();
				while (it.hasNext()) {
					String update = it.next();
					writer.write(update + ", ");
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (latexDiffCrawler.HTTPDownloadUtility.compareHashes(lastPath, path)) {
			// delete
			File slaveFile = new File(path);
			slaveFile.delete();
			System.out.println("deleted file because there was no update");
		} else
			lastPath = path;
	}

	/*
	 * checks for updates, writes the possibly updated List to a file and then
	 * sleeps the specified sleepTime does this until the LocalDateTime soon
	 */
	public static void checkAndSleep(ArrayList<String> ctanMirrors, ArrayList<ArrayList<String>> updateList,
			LocalDateTime soon, long sleepTime) {
		LocalDateTime today = LocalDateTime.now();
		while (!today.isAfter(soon)) {
			updateList = checkForUpdates2(ctanMirrors, updateList);
			today = LocalDateTime.now();
			String version = Integer.toString(today.getHour());
			version += Integer.toString(today.getMinute());
			writeToFile2(updateList, version);
			try {
				System.out.println("now sleepy");
				Thread.sleep(sleepTime);
				System.out.println("woke up at " + LocalDateTime.now().toString());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * puts all the single mirror update files into one big file
	 */
	public static void makeOneBeautifulFile(String path) {
		File file = new File(path);
		String[] files = file.list();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(path + "\\allUpdates.txt"));
			for (String updateFile : files) {
				InputStream is = Files.newInputStream(Paths.get(path + "\\" + updateFile));
				BufferedReader in = null;
				if (is != null) {
					in = new BufferedReader(new InputStreamReader(is));
					String inputLine = "";
					if (in != null) {
						writer.write(updateFile + "\n");
						while ((inputLine = in.readLine()) != null) {
							writer.write(inputLine + "\n");
						}
						writer.write("\n\n");
					}

				}
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * creates a separate update file for every mirror that is a bit more readable
	 * because the big file with all entries and all updates is very ugly
	 */
	public static void makeHumanReadableFiles() {
		try {
			InputStream is = Files.newInputStream(Paths.get(lastPath));
			BufferedReader in = null;
			if (is != null) {
				in = new BufferedReader(new InputStreamReader(is));
				String inputLine = "";
				if (in != null) {
					ArrayList<String> mirrorUpdates = new ArrayList<String>();
					while ((inputLine = in.readLine()) != null) {
						mirrorUpdates.add(inputLine);
					}
					for (String update : mirrorUpdates) {
						String entries[] = update.split(",");
						String mirrorName = entries[0].split("/")[2];
						String path = "C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\UpdateFiles\\mirrorUpdates\\"
								+ mirrorName;
						BufferedWriter writer = new BufferedWriter(new FileWriter(path));
						writer.write("Changed at:\t\t\t" + " Timestamp\n");
						writer.write("-\t\t\t\t" + entries[1] + "\n");
						for (int i = 2; i < entries.length - 1; i++) {
							String entry = entries[i];
							if (i % 2 == 0) {
								writer.write(entry.substring(entry.indexOf('2'), entry.indexOf('.')));
							} else {
								writer.write("\t\t" + entry + "\n");
							}
						}
						writer.close();
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * a program that checks a whole day for updates of CTAN mirrors and writes the
	 * update history in a text file
	 */
	public static void main(String[] args) {
		ArrayList<String> ctanMirrors = null;
		try {
			ctanMirrors = initCtanArray(Constants.fileWithCtanMirrors);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (ctanMirrors != null && ctanMirrors.size() > 0) {

			ArrayList<ArrayList<String>> updateList = new ArrayList<ArrayList<String>>();
			for (int i = 0; i < ctanMirrors.size(); i++) {

				updateList.add(new ArrayList<String>());
				String ctanMirror = ctanMirrors.get(i);
				mirrors.add(new Mirror(ctanMirror));
				updateList.get(i).add(ctanMirror + ": ");
				String update = "no connection";
				mirrors.get(i).setTimestamp();
				if (mirrors.get(i).getTimestamp() != null)
					update = mirrors.get(i).getTimestamp().toString();
				updateList.get(i).add(update);
			}
			checkAndSleep(ctanMirrors, updateList, LocalDateTime.now().plusDays(1), 300000); // check for updates, wait 5 minutes
			makeHumanReadableFiles();
			makeOneBeautifulFile("C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\UpdateFiles\\mirrorUpdates");
		}
	}

}
