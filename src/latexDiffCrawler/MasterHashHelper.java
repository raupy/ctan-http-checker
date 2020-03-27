package latexDiffCrawler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Hashtable;

/*
 * a class that calculates the hashes for the local master files
 * so that they don't have to be recalculated every time
 * stores them in @map with the filenames as keys and the hashes as values
 * and saves them locally in the MASTER_HASHES file
 */
public class MasterHashHelper {
	private Hashtable<String, Long> map;
	private List<String> files;
	boolean removeObsolete;
	private Mirror Dante;
	public static String fontsGreekKdInstall = "fonts/greek/kd/INSTALL";
	boolean didLoad = false;

	/*
	 * with @removeObsolete you can determine if you want to load the obsolete files
	 * starting with "obsolete/" these are ~20000 files which one should not use
	 * anymore
	 */
	public MasterHashHelper(boolean removeObsolete) {
		this.removeObsolete = removeObsolete;
		Dante = new Mirror(Constants.DANTE);
		files = HTTPDownloadUtility.getFilesFromFILES(Dante.getFILES_url(), removeObsolete);
	}

	/*
	 * in the case that there is already a MASTER_HASHES file, the @map will be load
	 * from this file and then it will be updated; otherwise it will be initialized
	 */
	public void loadOrInitMap() {
		if(!didLoad) didLoad = loadMap();
		if (didLoad)
			updateMap();
		// no master hash file
		else
			initMap();
		saveDifficultFiles();
	}

	/*
	 * updates the @map with the rsync log file and saves it to the local
	 * MASTER_HASHES file
	 */
	private void updateMap() {
		List<String> updatedFiles = getUpdatedFilesFromLogFile();
		for (String line : updatedFiles) {
			String fileURI = line.substring(line.lastIndexOf(" ") + 1, line.length());
			if(line.contains("*deleting")) {
				map.remove(fileURI);
				files.remove(fileURI);
			}
			else {
				if (removeObsolete) {
					if (!fileURI.startsWith("obsolete/"))
						computeHashAndAddToMap(fileURI);
				} else
					computeHashAndAddToMap(fileURI);
			}
		}
		System.out.println(map.size());
		System.out.println(files.size());
		saveHashes();
	}

	/*
	 * initializes the @map with the @files size and some extra space and saves it
	 * to the MASTER_HASHES file
	 */
	private void initMap() {
		map = new Hashtable<String, Long>(files.size() + 5000, 1);
		for (String file : files) {
			computeHashAndAddToMap(file);
		}
		saveHashes();
	}

	private List<String> getUpdatedFilesFromLogFile() {
		InputStream is = null;
		List<String> files = new ArrayList<String>();
		String inputLine;
		try {
			is = Files.newInputStream(Paths.get(Constants.RSCYNC_LOG));
			if (is != null) {
				BufferedReader in = new BufferedReader(new InputStreamReader(is));
				if (in != null) {
					while ((inputLine = in.readLine()) != null) {
						if (inputLine.contains(">f") || inputLine.contains("*deleting"))
							files.add(inputLine);
						else if(inputLine.contains("fonts/greek/kd/INSTALL\" is a directory")) {
//							HTTPDownloadUtility.downloadFile(Dante.getUrl() + fontsGreekKdInstall,
//									Constants.MASTER_DIR + "\\" + workAround);
							long masterChecksum = HTTPDownloadUtility.getHash(Constants.MASTER_DIR + "\\" + fontsGreekKdInstall, fontsGreekKdInstall);
							map.put(fontsGreekKdInstall, masterChecksum);
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return files;
	}

	/*
	 * gets the Adler checksum for the @file and puts the pair to the @map
	 */
	private void computeHashAndAddToMap(String file) {
		long masterChecksum = HTTPDownloadUtility.getHash(Constants.MASTER_DIR + "\\" + file, file);
		if(masterChecksum == 0) System.out.println("0 as checksum for " + file);
		map.put(file, masterChecksum);
	}

	public Hashtable<String, Long> getMap() {
		return map;
	}

	public List<String> getFiles() {
		return files;
	}


	/*
	 * tries to read the MASTER_HASHES file and loads the @map returns true if
	 * loading was successful
	 */
	public boolean loadMap() {
		InputStream is = null;
		String inputLine;
		boolean didLoad = false;
		try {
			is = Files.newInputStream(Paths.get(Constants.MASTER_HASHES));
			if (is != null) {
				BufferedReader in = new BufferedReader(new InputStreamReader(is));
				if (in != null) {
					map = new Hashtable<String, Long>(files.size() + 5000, 1);
					while ((inputLine = in.readLine()) != null) {
						String[] split = inputLine.split(" ,,, ");
						map.put(split[0], Long.parseUnsignedLong(split[1]));
					}
					didLoad = true;
				}
			}
		} catch (IOException e) {
			System.out.println("No master hash file yet, start computing it. This is going to take some minutes.");
		}
		return didLoad;
	}

	/*
	 * saves @map to the MASTER_HASHES file so that every line is a key value pair
	 * from @map
	 */
	public void saveHashes() {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.MASTER_HASHES));
			for (String file : map.keySet()) {
				writer.write(file + " ,,, " + map.get(file) + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void saveDifficultFiles() {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.DIFFICULT_FILES));
			for (String file : Main.difficultFiles) {
				writer.write(file + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
