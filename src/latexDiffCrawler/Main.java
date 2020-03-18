package latexDiffCrawler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import timestamps.TimestampUpdateChecker;

public class Main {

	static ArrayList<String> mirrorsToCheck = new ArrayList<String>();
	static ArrayList<Mirror> mirrors;
	static ArrayList<Mirror> equalTSmirrors;

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

	public static List<MirrorReader> getReader(List<String> files, int size, int divisor) {
		int quot = size / divisor;
		List<MirrorReader> readers = new ArrayList<MirrorReader>();
		for (int i = 0; i < divisor; i++) {
			List<String> part_i = new ArrayList<String>();
			int index;
			if (!(i == divisor - 1))
				index = (i + 1) * quot;
			else
				index = size - 1;
			part_i = files.subList(i * quot, index);
			readers.add(new MirrorReader(equalTSmirrors, part_i, size));
		}
		return readers;
	}

	public static ArrayList<Mirror> findEqualTimeStampMirrors() {
		ArrayList<Mirror> equalTSMirrors = new ArrayList<Mirror>();
		for (Mirror mirror : mirrors) {
			if (MirrorReader.compareTimeStamps(mirror)) {
				System.out.println(mirror.getName() + "has equal timestamp");
				equalTSMirrors.add(mirror);
			} else {
				System.out.println(mirror.getName() + "has other timestamp: " + mirror.getTimestamp());
			}
		}
		return equalTSMirrors;
	}

	// an idea: skip crawling and go through
	// http://dante.ctan.org/tex-archive/FILES.byname but here you possibly can't be
	// sure if there is really every file listed

	// this was a try to group some mirrors together (the one with the 7 o'clock
	// timestamp) and check all of them
	// but this is very very slow :(
	// most of these mirrors will have their next update only four hours later and
	// this won't fit at all
	// --> I can't check so many at a time (maybe like 3 at most or so?)
	// and I had to implement Threads (I am not sure how much I can have in order to
	// avoid DoS.. right now I have 9 which still needs 6 hours to check one Mirror)
	// idea: dont check obsolete/... because one should not use these packages
	// anymore

	// TODO: Big problem: to compare the big 4 (they update every hour) within one
	// hour
	// TODO: do the TODO in the Mirror class
	// TODO: make file with with computed hashes for the master file, so they don't
	// have to be computed anymore; update them with 
	// http://dante.ctan.org/tex-archive/FILES.last07days
	//
	public static void main(String[] args) {
		mirrors = init();

		// TODO:
//		while(mirrors != null && !mirrors.isEmpty()) {
//				new MasterRSync().download();
//				...
//				if(updatedList.size() == mirrors.size()) sleepUntilXX03(); 
//			}

		equalTSmirrors = Main.findEqualTimeStampMirrors();
		Mirror Dante = new Mirror(Constants.DANTE);
		List<String> files = HTTPDownloadUtility.getFilesFromFILES(Dante.getFILES_url(), true);
		int size = files.size();
		int divisor = 9;
		List<MirrorReader> readers = getReader(files, size, divisor);
		for (MirrorReader reader : readers) {
			reader.setMirrorReaders(readers);
			reader.start();
		}
		int checkedFiles = 0;
		while (checkedFiles <= size) {
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
	}
}
