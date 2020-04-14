package latexDiffCrawler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
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
import java.util.List;

public class Main {

	static MasterHashHelper msh;
	static ArrayList<Mirror> mirrors;
	static ArrayList<Mirror> equalTSmirrors;
	static List<String> difficultFiles = new ArrayList<String>();
	static List<String> blacklist = new ArrayList<String>();
	static int offset = -1;

	/*
	 * initialises the @mirrors list with the data from the mirrorData.txt file
	 */
	static ArrayList<Mirror> init() {
		ArrayList<String> mirrorData = new ArrayList<String>();
		ArrayList<Mirror> mirrors = null;
		loadListFromFile(Constants.mirrorData, mirrorData);
		if (mirrorData.size() > 0) {
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

	/*
	 * loads the name of the files that are "difficult", meaning that they have a
	 * bad name that provokes conflicts with the file system
	 */
	public static void loadDifficultFiles() {
		loadListFromFile(Constants.DIFFICULT_FILES, difficultFiles);
	}

	/*
	 * loads the names of the mirrors that were already checked or should not be
	 * checked due to some reasons
	 */
	public static void loadBlacklist() {
		loadListFromFile(Constants.BLACKLIST, blacklist);
	}

	public static void saveBlacklist() {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.BLACKLIST));
			for (String file : blacklist) {
				writer.write(file + "\n");
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * loads a @file and puts every line as an element into @list
	 */
	public static void loadListFromFile(String file, List<String> list) {
		try {
			InputStream is = Files.newInputStream(Paths.get(file));
			BufferedReader in = null;
			if (is != null) {
				in = new BufferedReader(new InputStreamReader(is));
				String inputLine = "";
				if (in != null) {
					while ((inputLine = in.readLine()) != null) {
						list.add(inputLine);
					}
				}
			}
		} catch (IOException e) {
		}
	}

	public static void removeBlacklistMirrors() {
		for (String name : blacklist) {
			mirrors.removeIf(m -> m.getName().equals(name));
		}
	}

	// the thread sleeps until it's the minute of the time is 03, so that there was
	// an update
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

	// the thread sleeps until the LocalDateTime @ldt
	public static void sleepUntilLocalDateTime(LocalDateTime ldt) {
		LocalDateTime now = LocalDateTime.now();
		long millisToSleep = now.until(ldt, ChronoUnit.MILLIS);
		long minutesToSleep = millisToSleep / 60000;
		try {
			System.out.println("I am waiting now " + minutesToSleep + " minutes until " + ldt.toString()
					+ ". Then every CTAN mirror from the list above should have an equal timestamp.");
			Thread.sleep(millisToSleep);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Creates a List of MirrorReader instances, each one with different files to
	 * check. For that, the original @files list is divided with the @divisor into
	 * equal (except maybe the last one) sublists.
	 */
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
			readers.add(new MirrorReader(equalTSmirrors, part_i, msh.getMap()));
		}
		return readers;
	}

	/*
	 * Returns an ArrayList of the Mirror instances from the @equalTSmirrors list
	 * for which the time stamps are the same as for the local Dante repo.
	 */
	public static ArrayList<Mirror> findEqualTimeStampMirrors() {
		ArrayList<Mirror> list = new ArrayList<Mirror>();
		for (Mirror mirror : equalTSmirrors) {
			if (MirrorReader.compareTimeStamps(mirror)) {
				System.out.println(mirror.getName() + " has an equal timestamp.");
				list.add(mirror);
			} else {
				System.out.println(mirror.getName() + " has a different timestamp: " + mirror.getTimestamp());
			}
		}
		return list;
	}

	// starts the comparing process
	// when the process is finished, it removes the compared mirrors from the
	// @mirrors lis
	private static void startReading() {
		List<String> files = msh.getFiles();
		int size = files.size();
		int divisor = 25; // number of MirrorReader Threads
		if (equalTSmirrors.size() == 1 && equalTSmirrors.get(0).getPrio() == 1) {
			if (equalTSmirrors.get(0).getName().equals("mirror.yongbok.net")
					|| equalTSmirrors.get(0).getName().equals("mirrors.cqu.edu.cn"))
				divisor = 40;
			else
				divisor = 30;
		}
		List<MirrorReader> readers = createReaders(files, size, divisor);
		List<TimeStampThread> tsThreads = new ArrayList<TimeStampThread>();
		startThreads(readers, tsThreads);
		checkAndSleep(size, readers);
		for (TimeStampThread tsThread : tsThreads) {
			if (tsThread.isAlive())
				tsThread.exit();
		}
		List<Mirror> outOfSyncMirrors = readers.get(0).getOutOfSyncMirrors();
		for (Mirror mirror : equalTSmirrors) {
			if (!outOfSyncMirrors.contains(mirror)) {
				blacklist.add(mirror.getName());
				mirrors.remove(mirror);
			}

		}
		saveBlacklist();

	}

	private static void startThreads(List<MirrorReader> readers, List<TimeStampThread> tsThreads) {
		for (Mirror mirror : equalTSmirrors) {
			TimeStampThread tsThread = new TimeStampThread(mirror, readers);
			tsThreads.add(tsThread);
			tsThread.start();
		}
		for (MirrorReader reader : readers) {
			reader.setMirrorReaders(readers);
			reader.start();
		}
	}

	// Checks if all files are checked and sleeps for 5 minutes if they aren't.
	private static void checkAndSleep(int size, List<MirrorReader> readers) {
		int checkedFiles = 0;
		float progress = 0;
		System.out
				.println("Comparing process is starting now. This is going to take around 60 minutes for one mirror.");
		while (progress < 99.99 && readers.get(0).getOutOfSyncMirrors().size() < equalTSmirrors.size()) {
			int min = 5;
			try {
				Thread.sleep(min * 60000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			for (MirrorReader reader : readers) {
				checkedFiles += reader.getCheckedFiles();
			}
			progress = ((float) checkedFiles / size) * 100;
			System.out.println(checkedFiles + " are checked = " + progress + " %.");
			checkedFiles = 0;
		}
	}

	/*
	 * Returns a list with the Mirror instances with the highest priority that could
	 * have the same time stamp as the local master repo. Waits until they should
	 * have that time stamp.
	 */
	public static ArrayList<Mirror> getEqualTSMirrorsAndWait() {
		int hourOfMasterTS = Main.hourOfMasterTimeStamp();
		ArrayList<Mirror> list = null;
		if (hourOfMasterTS != -1) {
			list = magicController(hourOfMasterTS);
			if (list != null) {
				System.out.println(
						"The following mirrors have the highest priority right now. They should have (maybe not yet) a timestamp at "
								+ hourOfMasterTS + " o'clock like the local Master.");
				for (Mirror mirror : list)
					System.out.println("Mirror " + mirror.getName());
			}
			waitForLatestMirror(list, hourOfMasterTS);
		}
		return list;
	}

	/*
	 * Returns the hour of the local master time stamp or -1 if there is none. The
	 * offset is the difference between that time stamp and the hour of the actual
	 * time the time stamp was made. It was a long time -1, but it can change of
	 * course. The mirror instances have to be notified when the offset changed
	 * because the data from mirrorData that was used to initialise them used the
	 * standard offset of -1.
	 */
	public static int hourOfMasterTimeStamp() {
		LocalDateTime masterTS = MirrorReader.getMasterTimeStamp();
		int hourOfTS = -1;
		if (masterTS != null) {
			LocalTime now = LocalTime.now();
			if (now.getMinute() < 2)
				now = now.minusHours(1);
			hourOfTS = masterTS.getHour();
			int newOffset;
			if (now.getHour() < masterTS.getHour()) {
				// like it's a new day and the time stamp is still at the day before
				// like new day at 00:30 and the time stamp is 22:02
				int bigHour = 24 + now.getHour();
				newOffset = hourOfTS - bigHour;
			} else
				newOffset = hourOfTS - now.getHour();
			if (newOffset != offset) {
				for (Mirror mirror : mirrors) {
					mirror.notifyOffsetChanged(offset, newOffset);
				}
				offset = newOffset;
			}
		}
		return hourOfTS;
	}

	/*
	 * Returns a list of Mirrors that could have a time stamp for the
	 * specific @hourOfTS. Only returns those with the highest priority.
	 */
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
				for (Mirror mirror : removeThese)
					list.remove(mirror);
			}
		}
		return list;
	}

	/*
	 * Estimates the time for the mirrors' (that are part of @list) updates, so that
	 * they have the same time stamp as the local master repo. Waits until the
	 * latest update should be finished.
	 */
	public static void waitForLatestMirror(ArrayList<Mirror> list, int hourOfMasterTS) {
		if (list == null) {
			Main.sleepUntilXX03();
		} else {
			LocalTime time = LocalTime.now();
			LocalDate today = LocalDate.now();
			LocalDate tomorrow = today.plusDays(1);
			LocalDateTime x = LocalDateTime.of(today, time);
			for (Mirror mirror : list) {
				mirror.setTimestamp();
				if (!MirrorReader.compareTimeStamps(mirror)) {
					LocalTime mirrorUpdate = mirror.getTimeStampRealTimeRelation().get(hourOfMasterTS);
					LocalDateTime y;
					if (mirrorUpdate.isBefore(time)) {
						y = LocalDateTime.of(tomorrow, mirrorUpdate);
					} else {
						y = LocalDateTime.of(today, mirrorUpdate);
					}
					if (y.isAfter(x) && LocalDateTime.now().until(y, ChronoUnit.MINUTES) < 200)
						x = y;
				}
			}
			if (x.isAfter(LocalDateTime.of(today, time))) {
				if (list.size() == 1 && list.get(0).getPrio() == 1)
					Main.sleepUntilLocalDateTime(x.plusMinutes(3));
				else
					Main.sleepUntilLocalDateTime(x.plusMinutes(10));
			}

		}

	}

	// skips crawling and go through
	// http://dante.ctan.org/tex-archive/FILES.byname

	// TODO: MirrorReader class: react to different http error codes like 400 / 500

	public static void main(String[] args) {
		mirrors = init();
		loadDifficultFiles();
		loadBlacklist();
		removeBlacklistMirrors();
		MasterRSync mrs = new MasterRSync();

		while (mirrors != null && !mirrors.isEmpty()) {
			mrs.download();
			msh = mrs.getMasterHashHelper();
			equalTSmirrors = Main.getEqualTSMirrorsAndWait();
			ArrayList<Mirror> potentialMirrors = Main.findEqualTimeStampMirrors();
			int i = 0;
			for (i = 0; i < 5 && (potentialMirrors == null || potentialMirrors.isEmpty()); i++) {
				try {
					Thread.sleep(300000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				potentialMirrors = Main.findEqualTimeStampMirrors();
			}
			if (i == 5 && (potentialMirrors == null || potentialMirrors.isEmpty())) {
				if (!mrs.shouldSync(MirrorReader.getMasterTimeStamp()))
					sleepUntilXX03();
				continue;
			}
			equalTSmirrors = potentialMirrors;
			startReading();
		}

	}
}
