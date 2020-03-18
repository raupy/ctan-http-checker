package latexDiffCrawler;

import java.time.LocalDateTime;
import java.util.List;

public class TimeStampThread extends Thread {

	private Mirror mirror;
	private List<MirrorReader> mirrorReaders;
	private boolean exit = false;
	
	public TimeStampThread(Mirror mirror, List<MirrorReader> mirrorReaders) {
		setMirror(mirror);
		setMirrorThread(mirrorReaders);
	}
	
	public void setMirror(Mirror mirror) {
		this.mirror = mirror;
	}
	
	public void setMirrorThread(List<MirrorReader> mirrorReaders) {
		this.mirrorReaders = mirrorReaders;
	}
	
    public void run(){
       while(!exit) {
    	   if(mirror.setTimestamp()) {
    		   for(MirrorReader reader : mirrorReaders) {
    			   reader.removeMirror(mirror);
    		   }
    	   }
    	   sleepUntilNextTimeStamp();
       }
    }
    
    public void exit() {
    	exit = true;
    }
    
    public void sleepUntilNextTimeStamp() {
		LocalDateTime now = LocalDateTime.now();
		int mi = now.getMinute();
		int sleepTime = 0;
		if (mi <= 2) {
			sleepTime = 3 - mi;
		} else {
			sleepTime = 60 - mi + 3;
		}
		try {
			System.out.println(this.getClass() + " for Mirror " + mirror.getName() + " now sleepy for " + sleepTime + " minutes until " + now.plusMinutes(sleepTime).toString());
			Thread.sleep(sleepTime * 60 * 1000); // sleep sleepTime in milliseconds
			System.out.println(this.getClass() + " for Mirror " + mirror.getName() + " woke up at " + LocalDateTime.now().toString());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
    
    
  }
