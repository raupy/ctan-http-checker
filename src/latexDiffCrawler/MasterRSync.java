package latexDiffCrawler;

import java.io.File;
import java.time.LocalDateTime;

import com.github.fracpete.processoutput4j.output.CollectingProcessOutput;
import com.github.fracpete.rsync4j.RSync;

/*
 * This is a class that updates the local Dante repo with the online changes.
 * Also tells the MasterHashHelper @msh about the changes.
 */
public class MasterRSync {

	private MasterHashHelper msh;

	public MasterRSync() {
		msh = new MasterHashHelper(true);
	}

	public MasterHashHelper getMasterHashHelper() {
		return msh;
	}

	/*
	 * Returns true if the local and the online time stamps of the Dante server are
	 * different, so that the local repo should be synchronized.
	 */
	private boolean shouldSync(LocalDateTime lastUpdate) {
		boolean shouldSync = true;
		if (lastUpdate != null) {
			LocalDateTime now = LocalDateTime.now();
			int nowMin = now.getMinute();
			if (nowMin <= 1) {
				int sleepTime = 2 - nowMin;
				try {
					Thread.sleep(sleepTime * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			Mirror Dante = new Mirror(Constants.DANTE);
			Dante.setTimestamp();
			LocalDateTime onlineTS = Dante.getTimestamp();
			if (onlineTS.isEqual(lastUpdate)) {
				shouldSync = false;
			}
		}
		return shouldSync;
	}

	/*
	 * Starts Rsync with Dante if there is a new update available. Loads or
	 * initialises the Hashtable from the MasterHashHelper @msh.
	 */
	public void download() {
		LocalDateTime lastUpdate = MirrorReader.getMasterTimeStamp();
		if (shouldSync(lastUpdate)) {
			sync();
			if (lastUpdate == null) { // the very first time that the program is started
				// it takes much longer than the usual ~2 minutes for the sync
				// so it could be possible that there is again a new update availabe
				download();
				return;
			}
		}
		msh.loadOrInitMap();
	}

	/*
	 * rsync with Dante server, deletes the old logFile and saves the new one
	 */
	private void sync() {
		File log = new File(Constants.RSCYNC_LOG);
		if (log.exists())
			log.delete();
		RSync rsync = new RSync().source("rsync://rsync.dante.ctan.org/CTAN/").humanReadable(true)
				.destination(Constants.MASTER_DIR).recursive(true).verbose(true).delete(true).archive(true)
				.progress(true).outputCommandline(true).logFile(Constants.RSCYNC_LOG);
		try {
			CollectingProcessOutput output = rsync.execute();
			if (output.getExitCode() > 0) {
				System.out.println(output.getStdErr());
			} else {
				System.out.println(output.getStdOut());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
