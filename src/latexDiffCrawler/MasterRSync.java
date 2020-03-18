package latexDiffCrawler;

import com.github.fracpete.processoutput4j.output.CollectingProcessOutput;
import com.github.fracpete.rsync4j.RSync;

public class MasterRSync {
	
	public MasterRSync() {
		
	}
	
	
	public void download() {
		RSync rsync = new RSync()
				  .source("rsync://rsync.dante.ctan.org/CTAN/").humanReadable(true)
				  //.destination("/cygdrive/e/slaverepo/master")
				  .destination(Constants.MASTER_DIR)
				  .recursive(true).verbose(true).delete(true)
				  .archive(true).progress(true).outputCommandline(true);
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
