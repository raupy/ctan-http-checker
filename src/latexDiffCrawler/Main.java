package latexDiffCrawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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

	// initializes the list of http / https CTAN Mirrors
	public static ArrayList<Mirror> init() {
		ArrayList<String> ctanMirrors = null;
		ArrayList<Mirror> mirrors = null;
		try {
			ctanMirrors = TimestampUpdateChecker.initCtanArray(Constants.fileWithCtanMirrors);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (ctanMirrors != null && ctanMirrors.size() > 0) {
			mirrorsToCheck = ctanMirrors;
			mirrors = updateMirrorList();
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
		ArrayList<Mirror> equalTSMirrors = new ArrayList<Mirror>();
		for (Mirror mirror : mirrors) {
			if (MirrorReader.compareTimeStamps(mirror)) {
				System.out.println(mirror.getName() + " has equal timestamp");
				equalTSMirrors.add(mirror);
			} else {
				System.out.println(mirror.getName() + " has other timestamp: " + mirror.getTimestamp());
			}
		}
		return equalTSMirrors;
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
		while (checkedFiles <= size && ((float) checkedFiles / size) < 1) {
			checkedFiles = 0;
			for (MirrorReader reader : readers) {
				checkedFiles += reader.getCheckedFiles();
			}
			System.out.println(checkedFiles + " are checked = " + ((float) checkedFiles / size) * 100 + " %.");
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		mirrors.removeAll(equalTSmirrors);
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
			mrs.download();
			msh = mrs.getMasterHashHelper();
			equalTSmirrors = Main.findEqualTimeStampMirrors();
			if (equalTSmirrors.isEmpty()) {
				sleepUntilXX03();
				continue;
			}
			int maxMirrors = 1;
			if (equalTSmirrors.size() > maxMirrors) {
				ArrayList<Mirror> checkOnlyTheseMirrors = new ArrayList<Mirror>();
				checkOnlyTheseMirrors.add(equalTSmirrors.get(maxMirrors - 1));
				equalTSmirrors = checkOnlyTheseMirrors;
			}
			startReading();
		}

	}
}
