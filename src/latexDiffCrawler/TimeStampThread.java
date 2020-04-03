package latexDiffCrawler;

import java.util.List;

/*
 * This class implements a thread that looks for a changing time stamp for a specific @mirror
 * or if the mirror is currently offline. In both cases the thread informs every @MirrorReader
 * that is reading from the specific @mirror.
 * It terminates when one of the cases above is true or if it its exit()-method is called.
 */
public class TimeStampThread extends Thread {

	private Mirror mirror;
	private List<MirrorReader> mirrorReaders;
	private boolean exit = false;

	public TimeStampThread(Mirror mirror, List<MirrorReader> mirrorReaders) {
		this.mirror = mirror;
		this.mirrorReaders = mirrorReaders;
	}

	public void run() {
		while (!exit) {
			sleepMinutes(10);
			if (mirror.setTimestamp()) {
				for (MirrorReader reader : mirrorReaders) {
					reader.removeMirror(mirror);
				}
				exit = true;
			}
		}
	}

	public void exit() {
		exit = true;
	}

	public void sleepMinutes(int minutes) {
		try {
			Thread.sleep(60 * 1000 * minutes); // sleep sixty seconds * minutes
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
