package latexDiffCrawler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import timestamps.TimestampUpdateChecker;

public class Main {

	// initializes the list of http / https CTAN Mirrors
	static ArrayList<Mirror> init() {
		ArrayList<String> ctanMirrors = null;
		try {
			ctanMirrors = TimestampUpdateChecker.initCtanArray(Constants.fileWithCtanMirrors);
		} catch (IOException e) {
			e.printStackTrace();
		}
		ArrayList<Mirror> mirrors = null;
		if (ctanMirrors != null && ctanMirrors.size() > 0) {
			mirrors = new ArrayList<Mirror>();
			for (String mirrorString : ctanMirrors) {
				Mirror mi = new Mirror(mirrorString);
				mirrors.add(mi);
			}
		}
		return mirrors;
	}

	// crawls to every CTAN mirror and compare them with Dante when they have the
	// same timestamp
	// downloads a file when the length at the mirror is not the same as at the
	// server
	// TODO: see how long it takes for one mirror. does it fit in one hour? because
	// dante is updated most of the time every hour i think
	// if not: download master with Oli's RSYNC and check again if it fits in one
	// hour
	// if not: download both master and mirror and compare them offline
	// also an idea: skip crawling and go through
	// http://dante.ctan.org/tex-archive/FILES.byname but here you possibly can't be
	// sure if there is really every file listed
	// UPDATE: it takes a looooot more than one our. Stopped at macros/latex after
	// two hours :/
	// so i guess this online approach won't work
	public static void main(String[] args) {
		ArrayList<Mirror> mirrors = init();
		if (mirrors != null) {
			Mirror Dante = new Mirror(Constants.DANTE);
			while (!mirrors.isEmpty()) {
				for (Mirror mirror : mirrors) {
//					LocalDateTime now = LocalDateTime.now();
//					if (now.getMinute() >= 30 || now.getMinute() <= 2) { //update time is at xx:02
//						//TODO: sleep until xx:03
//					}
					LocalDateTime mirrorTimestamp = mirror.getTimestamp();
					if (mirrorTimestamp != null) {
						LocalDateTime masterTimestamp = Dante.getTimestamp();
						if (masterTimestamp != null) {
							if (mirrorTimestamp.equals(masterTimestamp)) {
								mirrors.remove(mirror);
								HTTPReader httpReader = new HTTPReader(mirror);
								httpReader.crawlAndDownload();
							}

						}
					}
				}
			}
		}

		// test the code with only one mirror
//		String url = "https://ctan.crest.fr/tex-archive/";
//		HTTPReader httpReader = new HTTPReader(new Mirror(url));
//		ArrayList<String> files = httpReader.crawlAndDownload();
//		System.out.println("-----------------------------------------");
//		System.out.println("-----------------------------------------");
//		for (String s : files) {
//			//TODO try to download them again
//			System.out.println(s);
//		}
	}

}
