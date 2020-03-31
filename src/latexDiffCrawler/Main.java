package latexDiffCrawler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import timestamps.TimestampUpdateChecker;

public class Main {

	static MasterHashHelper msh;
	static ArrayList<String> mirrorsToCheck = new ArrayList<String>();
	static ArrayList<Mirror> mirrors;
	static ArrayList<Mirror> equalTSmirrors;
	static List<String> difficultFiles = new ArrayList<String>();
	static int offset = -1;

	// initializes the list of http / https CTAN Mirrors
//	public static ArrayList<Mirror> init() {
//		ArrayList<String> ctanMirrors = null;
//		ArrayList<Mirror> mirrors = null;
//		try {
//			ctanMirrors = TimestampUpdateChecker.initCtanArray(Constants.fileWithCtanMirrors);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		if (ctanMirrors != null && ctanMirrors.size() > 0) {
//			mirrorsToCheck = ctanMirrors;
//			mirrors = updateMirrorList();
//		}
//		return mirrors;
//	}

	public static ArrayList<String> readWholeFile(String fileWihMirrorData) throws IOException {
		ArrayList<String> mirrorsData = new ArrayList<String>();
		BufferedReader in = new BufferedReader(new FileReader(fileWihMirrorData));
		String inputLine = "";
		while ((inputLine = in.readLine()) != null)
			mirrorsData.add(inputLine);
		in.close();
		return mirrorsData;
	}

	static ArrayList<Mirror> init() {
		ArrayList<String> mirrorData = null;
		ArrayList<Mirror> mirrors = null;
		try {
			mirrorData = readWholeFile(Constants.mirrorData);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (mirrorData != null && mirrorData.size() > 0) {
			mirrors = new ArrayList<Mirror>();
			for (String md : mirrorData) {
				String[] data = md.split(" ,,, ");
				Mirror mi = new Mirror(data[0]);
				mi.setTimeStampInfos(data[1], data[2], data[3]);
				mirrors.add(mi);
			}

		}
		return mirrors;
	}

	public static void loadDifficultFiles() {
		try {
			InputStream is = Files.newInputStream(Paths.get(Constants.DIFFICULT_FILES));
			BufferedReader in = null;
			if (is != null) {
				in = new BufferedReader(new InputStreamReader(is));
				String inputLine = "";
				if (in != null) {
					while ((inputLine = in.readLine()) != null) {
						difficultFiles.add(inputLine);
					}
				}
			}
		} catch (IOException e) {
		}
	}

	public static ArrayList<Mirror> updateMirrorList() {
		ArrayList<Mirror> mirrors = null;
		if (mirrorsToCheck != null && mirrorsToCheck.size() > 0) {
			mirrors = new ArrayList<Mirror>();
			for (String mirrorString : mirrorsToCheck) {
				Mirror mi = new Mirror(mirrorString);
				mirrors.add(mi);
			}
		}
		return mirrors;
	}

	public static void sleepUntilXX03() {
		LocalDateTime now = LocalDateTime.now();
		int mi = now.getMinute();
		int sleepTime = 0;
		if (mi <= 2) {
			sleepTime = 3 - mi;
		} else {
			sleepTime = 60 - mi + 3;
		}
		try {
			System.out.println("No mirror is up to date. Now sleepy for " + sleepTime + " minutes until "
					+ now.plusMinutes(sleepTime).toString());
			Thread.sleep(sleepTime * 60 * 1000); // sleep sleepTime in milliseconds
			System.out.println("Main thread woke up at " + LocalDateTime.now().toString());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void sleepUntilLocalDateTime(LocalDateTime ldt) {
		LocalDateTime now = LocalDateTime.now();
		long millisToSleep = now.until(ldt, ChronoUnit.MILLIS);
		long minutesToSleep = millisToSleep / 60000;
		try {
			System.out.println("I am waiting now " + minutesToSleep + " minutes until " + ldt.toString()
			+ ". Then the CTAN mirror with the highest priority and the latest update should have a timestamp that is equal to the current Master timestamp");
			Thread.sleep(millisToSleep);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static List<MirrorReader> createReaders(List<String> files, int size, int divisor) {
		int quot = size / divisor;
		List<MirrorReader> readers = new ArrayList<MirrorReader>();
		for (int i = 0; i < divisor; i++) {
			List<String> part_i = new ArrayList<String>();
			int index;
			if (!(i == divisor - 1))
				index = (i + 1) * quot;
			else
				index = size;
			part_i = files.subList(i * quot, index);
			System.out.println(part_i.size());
			readers.add(new MirrorReader(equalTSmirrors, part_i, msh.getMap()));
		}
		return readers;
	}

	public static ArrayList<Mirror> findEqualTimeStampMirrors() {
		ArrayList<Mirror> list = new ArrayList<Mirror>();
		for (Mirror mirror : equalTSmirrors) {
			if (MirrorReader.compareTimeStamps(mirror)) {
				System.out.println(mirror.getName() + " has equal timestamp");
				list.add(mirror);
			} else {
				System.out.println(mirror.getName() + " has other timestamp: " + mirror.getTimestamp());
			}
		}
		return list;
	}

	private static void startReading() {
		List<String> files = msh.getFiles();
		int size = files.size();
		int divisor = 22;
		List<MirrorReader> readers = createReaders(files, size, divisor);
		for (MirrorReader reader : readers) {
			reader.setMirrorReaders(readers);
			reader.start();
		}
		int checkedFiles = 0;
		System.out.println("Comparing process is starting now. This is going to take between 30 - 60 minutes for one mirror.");
		while (checkedFiles <= size && ((float) checkedFiles / size) < 0.995) {
			checkedFiles = 0;
			for (MirrorReader reader : readers) {
				checkedFiles += reader.getCheckedFiles();
			}
			System.out.println(checkedFiles + " are checked = " + ((float) checkedFiles / size) * 100 + " %.");
			try {
				Thread.sleep(300000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		for(Mirror mirror : readers.get(0).getMirrors())
			mirrors.remove(mirror);
	}

	public static ArrayList<Mirror> getEqualTSMirrorsAndWait(){
		int hourOfMasterTS = Main.hourOfMasterTimeStamp();
		ArrayList<Mirror> list = magicController(hourOfMasterTS);
		for(Mirror mirror : list)
			System.out.println("Mirror " + mirror.getName());
		waitForLatestMirror(list, hourOfMasterTS);
		return list;
	}
	
	public static int hourOfMasterTimeStamp() {
		LocalDateTime masterTS = MirrorReader.getMasterTimeStamp();
		int hourOfTS = -1;
		if(masterTS != null) {
			LocalTime now = LocalTime.now();
			if(now.getMinute() < 2) 
				now = now.minusHours(1);
			hourOfTS = masterTS.getHour();
			int newOffset = hourOfTS - now.getHour();
			if(newOffset != offset) {
				for(Mirror mirror : mirrors) {
					mirror.notifyOffsetChanged(offset, newOffset);
				}
				offset = newOffset;
			}
		}
		return hourOfTS;
	}

	public static ArrayList<Mirror> magicController(int hourOfTS) {
		ArrayList<Mirror> list = new ArrayList<Mirror>();
		int biggestPrio = 1;
		for (Mirror mirror : mirrors) {
			if (mirror.getTimeStampAtIndex().get(hourOfTS) > 0) {
				int mirrorPrio = mirror.getPrio();
				if (mirrorPrio >= biggestPrio) {
					list.add(mirror);
					biggestPrio = mirrorPrio;
				}
			}
		}
		if (!list.isEmpty()) {
			if (biggestPrio == 1) {
				Mirror save = list.get(0);
				list.removeAll(list);
				list.add(save);
			} else {
				List<Mirror> removeThese = new ArrayList<Mirror>();
				for (Mirror mirror : list) {
					if (mirror.getPrio() < biggestPrio)
						removeThese.add(mirror);
				}
				for(Mirror mirror : removeThese)
					list.remove(mirror);
			}
		}
		return list;
	}

	public static void waitForLatestMirror(ArrayList<Mirror> list, int hourOfMasterTS) {
		if(list == null) {
			Main.sleepUntilXX03();
		}
		else {
			LocalTime time = LocalTime.now();
			LocalDate today = LocalDate.now();
			LocalDate tomorrow = today.plusDays(1);
			LocalDateTime x = LocalDateTime.of(today, time);
			for(Mirror mirror : list) {
				mirror.setTimestamp();
				if(!MirrorReader.compareTimeStamps(mirror)) {
					LocalTime mirrorUpdate = mirror.getTimeStampRealTimeRelation().get(hourOfMasterTS);
					LocalDateTime y;
					if(mirrorUpdate.isBefore(time)) {
						y = LocalDateTime.of(tomorrow, mirrorUpdate);
					}
					else {
						y = LocalDateTime.of(today, mirrorUpdate);
					}
					if(y.isAfter(x) && LocalDateTime.now().until(y, ChronoUnit.MINUTES) < 200) x = y;
				}
			}
			if(x.isAfter(LocalDateTime.of(today, time))) Main.sleepUntilLocalDateTime(x.plusMinutes(10));
		}
			
	}

	// skip crawling and go through
	// http://dante.ctan.org/tex-archive/FILES.byname but here you possibly can't be
	// sure if there is really every file listed

	// TODO: do the TODO in the Mirror class and the MirrorReader class

	public static void main(String[] args) {
		mirrors = init();
		loadDifficultFiles();
		MasterRSync mrs = new MasterRSync();
		
		while (mirrors != null && !mirrors.isEmpty()) {
//			mrs.download();
			msh = mrs.getMasterHashHelper();
			equalTSmirrors = Main.getEqualTSMirrorsAndWait();
			equalTSmirrors = Main.findEqualTimeStampMirrors();
			if (equalTSmirrors.isEmpty()) {
				sleepUntilXX03();
				continue;
			}
//			int maxMirrors = 1;
//			if (equalTSmirrors.size() > maxMirrors) {
//				ArrayList<Mirror> checkOnlyTheseMirrors = new ArrayList<Mirror>();
//				checkOnlyTheseMirrors.add(equalTSmirrors.get(maxMirrors - 1));
//				equalTSmirrors = checkOnlyTheseMirrors;
//			}
			startReading();
		}

	}
}
