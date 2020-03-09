package timestamps;

import java.net.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.*;
import latexDiffCrawler.Constants;
	
public class TimestampUpdateChecker {

	
	
	/*
	 * returns the time of the last update of this mirror that you can find under url/timestamp
	 * example for what the file looks like:
	 * # This file is for administrative purposes only.
	   #   The source CTAN of this site's material:
	   dante.ctan.org
	   #   The year-month-day-hour-minute of this site's material:
	   2020-02-05-06-02
	 * so this method just returns the last line of the file
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
        if(ctanMirror != null) {
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
	 * Initialises and returns the ArrayList of CTAN mirrors with the entries that are listed in the .txt file
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
	 * checks if any mirror was updated since the last check
	 * if yes, this timestamp will be added to the mirror timestamp history 
	 * returns an ArrayList of ArrayLists of Strings that contains this history for every mirror
	 */
	public static ArrayList<ArrayList<String>> checkForUpdates(ArrayList<String> ctanMirrors, ArrayList<ArrayList<String>> updateList) {
		for(int i = 0; i < ctanMirrors.size(); i++) {
			String ctanMirror = ctanMirrors.get(i);
			String timestamp =  getTimestamp(ctanMirror + "timestamp");
			ArrayList<String> mirrorI = updateList.get(i);
			int size = mirrorI.size();
			String lastUpdate = mirrorI.get(size - 1);
			if(!timestamp.equals(lastUpdate)) mirrorI.add(timestamp);
		}
		return updateList;
	}
	
	/*
	 * writes the result that is saved in updateList to a file
	 * version is just a safety thing for redundancy so that maybe if something crashes over night 
	 * or because of power loss or some thing not all results are lost
	 */
	public static void writeToFile(ArrayList<ArrayList<String>> updateList, String version) {
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter("C:\\Users\\Lilli\\eclipse-workspace\\LaTexCrawler\\src\\UpdateFile" + version + ".txt"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Iterator<ArrayList<String>> bigIt = updateList.iterator();
				while(bigIt.hasNext()) {
					Iterator<String> it = bigIt.next().iterator();
					while(it.hasNext()) {
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

	/*
	 * checks for updates, writes the possibly updated List to a file and then sleeps the specified sleepTime
	 * does this until the LocalDateTime soon 
	 */
	public static void checkAndSleep(ArrayList<String> ctanMirrors, ArrayList<ArrayList<String>> updateList, LocalDateTime soon, long sleepTime) {
		LocalDateTime today =  LocalDateTime.now(); 
		while(!today.isAfter(soon)) {
			updateList = checkForUpdates(ctanMirrors, updateList);
			today =  LocalDateTime.now();
			String version = Integer.toString(today.getHour());
			version += Integer.toString(today.getMinute());
			writeToFile(updateList, version);
			try {
				Thread.sleep(sleepTime); 
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
		}
	}
	
	/*
	 * a program that checks a whole day for updates of CTAN mirrors 
	 * and writes the update history in a text file
	 */
	public static void main(String[] args)  {
		ArrayList<String> ctanMirrors = null;
		try {
			ctanMirrors = initCtanArray(Constants.fileWithCtanMirrors);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(ctanMirrors != null && ctanMirrors.size() > 0) {
			ArrayList<ArrayList<String>> updateList = new ArrayList<ArrayList<String>>();
			for(int i = 0; i < ctanMirrors.size(); i++) {
				updateList.add(new ArrayList<String>());
				String ctanMirror = ctanMirrors.get(i);
				updateList.get(i).add(ctanMirror + ": ");
				updateList.get(i).add(getTimestamp(ctanMirror + "timestamp"));
			}
			checkAndSleep(ctanMirrors, updateList, LocalDateTime.now().plusDays(1), 900000); //check for updates, wait 15 minutes
		}
	}

	
}

