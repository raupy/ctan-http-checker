package latexDiffCrawler;

import java.io.File;
import java.time.LocalDateTime;

import com.github.fracpete.processoutput4j.output.CollectingProcessOutput;
import com.github.fracpete.rsync4j.RSync;

public class MasterRSync {

	private MasterHashHelper msh;

	public MasterRSync() {
		msh = new MasterHashHelper(true);
//		msh.loadMap();
	}

	public MasterHashHelper getMasterHashHelper() {
		return msh;
	}

	public void download() {
		LocalDateTime lastUpdate = MirrorReader.getMasterTimeStamp();
		boolean shouldSync = true;
		if (lastUpdate != null) {
			LocalDateTime now = LocalDateTime.now();
			int nowHour = now.getHour();
			int nowMin = now.getMinute();
			int hourLastUpdateRealTime = lastUpdate.getHour() - Main.offset;
			if (hourLastUpdateRealTime != nowHour) {
				if (hourLastUpdateRealTime + 1 == nowHour && nowMin <= 2) {
					int sleepTime = 2 - nowMin;
					try {
						Thread.sleep(sleepTime * 60 * 1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} else
				shouldSync = false;
		}
		if(shouldSync) {
			sync();
			if(lastUpdate == null) download();
		}
		msh.loadOrInitMap();
	}

	private void sync() {
		File log = new File(Constants.RSCYNC_LOG);
		log.delete();
		RSync rsync = new RSync().source("rsync://rsync.dante.ctan.org/CTAN/").humanReadable(true)
				// .destination("/cygdrive/e/slaverepo/master")
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
