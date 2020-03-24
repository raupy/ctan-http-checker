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
	private LocalDate lastUpdate;

	/*
	 * with @removeObsolete you can determine if you want to load the obsolete files
	 * starting with "obsolete/" these are ~20000 files which one should not use
	 * anymore
	 */
	public MasterHashHelper(boolean removeObsolete) {
		this.removeObsolete = removeObsolete;
		Dante = new Mirror(Constants.DANTE);
		loadOrInitMap();
	}

	/*
	 * depending of if there is already a MASTER_HASHES file with a last update that
	 * is not a week ago the @map will be load from the file and then will be
	 * updated OR it will be initialized again
	 */
	private void loadOrInitMap() {
		files = HTTPDownloadUtility.getFilesFromFILES(Dante.getFILES_url(), removeObsolete);
		String lastUpdateString = loadMap(true);
		if (lastUpdateString != null) {
			lastUpdate = this.localDateParser(lastUpdateString, "uuuu-MM-dd");
			if (lastUpdate.isBefore(LocalDate.now().minusDays(7))) {
				initMap();
			} else {
				updateMap();
			}

		}
		// no master hash file
		else {
			initMap();
		}
	}

	/*
	 * loads the @map and tries to update it with FILESlast07days if it doesnt work
	 * it initializes the @map again in both cases it saves the @map to the local
	 * MASTER_HASHES file
	 */
	private void updateMap() {
		String[] filesLast07Days = HTTPDownloadUtility.getFilesFromFILESlast07Days(Dante.getFILESlast07Days_url());
		if (filesLast07Days != null) {
			loadMap(false);
			for (String line : filesLast07Days) {
				LocalDate fileUpdate = this.localDateParser(line.substring(0, line.indexOf('|') - 1), "yyyy/MM/dd");
				if (fileUpdate.isBefore(lastUpdate.minusDays(1))) {
					break;
				} else {
					String fileURI = line.substring(line.lastIndexOf("|") + 2, line.length());
					if (removeObsolete) {
						if (!fileURI.startsWith("obsolete/"))
							computeHashAndAddToMap(fileURI);
					} else
						computeHashAndAddToMap(fileURI);
				}
			}
		} else { // could not download FILESlast07Days -> init again
			initMap();
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

	/*
	 * gets the Adler checksum for the @file and puts the pair to the @map
	 */
	private void computeHashAndAddToMap(String file) {
		long masterChecksum = HTTPDownloadUtility.getHash(Constants.MASTER_DIR + "\\" + file, file);
		map.put(file, masterChecksum);
	}

	public Hashtable<String, Long> getMap() {
		return map;
	}

	public List<String> getFiles() {
		return files;
	}

	public Hashtable<String, Long> getTable(int start, int end) {
		int i = 0;
		Hashtable<String, Long> table = new Hashtable<String, Long>();
		for (String file : map.keySet()) {
			if (i >= start && i < end)
				table.put(file, map.get(file));
			else if (i >= end)
				break;
			i++;
		}
		return table;
	}

	public LocalDate localDateParser(String timestamp, String pattern) { // pattern like e.g. "yyyy/MM/dd" or
																			// "yyyy-MM-dd"
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
		LocalDate date = LocalDate.parse(timestamp, formatter);
		return date;
	}

	/*
	 * reads the MASTER_HASHES file; if @getLastUpdateOnly is true, it only reads
	 * the first line which contains the last update; otherwise it reads the whole
	 * file and loads the @map
	 */
	public String loadMap(boolean getLastUpdateOnly) {
		InputStream is = null;
		String inputLine = null;
		try {
			is = Files.newInputStream(Paths.get(Constants.MASTER_HASHES));
			if (is != null) {
				BufferedReader in = new BufferedReader(new InputStreamReader(is));
				if (in != null) {
					inputLine = in.readLine(); // lastUpdate
					if (!getLastUpdateOnly) {
						map = new Hashtable<String, Long>(files.size() + 5000, 1);
						while ((inputLine = in.readLine()) != null) {
							String[] split = inputLine.split(" ,,, ");
							map.put(split[0], Long.parseUnsignedLong(split[1]));
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return inputLine;
	}

	/*
	 * saves @map to the MASTER_HASHES file the first line is the date of the last
	 * update and every line after that is a key value pair from @map
	 */
	public void saveHashes() {
		lastUpdate = LocalDateTime.now().toLocalDate();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.MASTER_HASHES));
			writer.write(lastUpdate.toString() + "\n");
			for (String file : map.keySet()) {
				writer.write(file + " ,,, " + map.get(file) + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
