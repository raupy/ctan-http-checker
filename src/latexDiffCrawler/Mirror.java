package latexDiffCrawler;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Mirror {

	private String url;
	private String name;
	private String directory;
	private String FILES_url;
	private LocalDateTime timestamp;
	private boolean fixTS = false;
	
	//TODO:
	private boolean wasChecked = false;
	private ArrayList<Float> hasTimeStampAtIndex = new ArrayList<Float>();
	private int prio;
	private Hashtable<Integer, LocalTime> timeStampRealTimeRelation;

	public Mirror(String url) {
		this.url = url;
		setName(url);
		if(!name.contains("dante.ctan.org")) setAndMakeDirectory(url);
		FILES_url = url + "FILES.byname";
	}
	
	public void notifyOffsetChanged(int offset, int newOffset) {
		int diff = offset - newOffset;
		changeRelation(diff);
		if(fixTS) { 
			//the mirror has a fix timestamp, so @hasTimeStampAtIndex has to remain the same
			//we just have to change the @timeStampRealTimeRelation
		}
		else {
			if(diff > 0) { //pos -> push array left 
				while(diff > 0) {
					float f = this.hasTimeStampAtIndex.remove(0);
					this.hasTimeStampAtIndex.add(f);
					diff--;
				}
			}
			else { //neg -> push array right
				while(diff < 0) {
					float f = this.hasTimeStampAtIndex.remove(23);
					List<Float> newTail = this.hasTimeStampAtIndex.subList(0, 22);
					this.hasTimeStampAtIndex.removeAll(newTail);
					this.hasTimeStampAtIndex.add(f);
					this.hasTimeStampAtIndex.addAll(newTail);
					diff++;
				}
			}
		}	
	}
	
	private void changeRelation(int diff) {
		if(fixTS) { //the key remains the same, the LocalTime value has to change
			for(Integer key : this.getTimeStampRealTimeRelation().keySet())
				this.timeStampRealTimeRelation.put(key, this.timeStampRealTimeRelation.get(key).plusHours(diff));	
		}
		else { //the LocalTime value remains the same, the key has to change
			Hashtable<Integer, LocalTime> newMap = new Hashtable<Integer, LocalTime>();
			for(Integer i : this.timeStampRealTimeRelation.keySet()) {
				LocalTime value = this.timeStampRealTimeRelation.get(i);
				if(i == 0 && diff > 0) i = 24;
				else if(i == 23 && diff < 0) i = - 1;
				newMap.put(i - diff, value);
			}
			this.timeStampRealTimeRelation = newMap;
		}
	}
	
	private void setTS(String trueOrFalse) {
		if(trueOrFalse.equals("true")) fixTS = true;
	}
	
	public void setTimeStampInfos(String timestamps, String updates, String trueOrFalse) {
		setTimeStampAtIndex(timestamps);
		setTimeStampRealTimeRelation(updates);
		setPrio();
		setTS(trueOrFalse);
	}
	
	private void setTimeStampRealTimeRelation(String updates) {
		timeStampRealTimeRelation = new Hashtable<Integer, LocalTime>();
		String[] splittedUpdates = updates.split(",");
		int j = 0;
		for(int i = 0; i < hasTimeStampAtIndex.size(); i++) {
			if(hasTimeStampAtIndex.get(i) > 0) {
				timeStampRealTimeRelation.put(i, localTimeParser(splittedUpdates[j], "HH:mm"));
				j++;
			}
		}
	}
	
	public Hashtable<Integer, LocalTime> getTimeStampRealTimeRelation(){
		return this.timeStampRealTimeRelation;
	}
	
	public int getPrio() {
		return prio;
	}
	
	private void setPrio() {
		float updatesPerDay = 0;
		for(float i : hasTimeStampAtIndex) {
			updatesPerDay += i;
		}
		prio = 24 / (int) updatesPerDay;
	}
	
	public boolean getWasChecked() {
		return wasChecked;
	}
	
	public void setWasChecked() {
		wasChecked = true;
	}
	
	public void setTimeStampAtIndex(String timestamps) {
		String[] splittedTimestamps = timestamps.split(",");
		for(String ts : splittedTimestamps) {
			if(ts.equals("0")) hasTimeStampAtIndex.add(0f);
			else if(ts.equals("0.5")) hasTimeStampAtIndex.add(0.5f);
			else hasTimeStampAtIndex.add(1f);
		}
	}
	
	public ArrayList<Float> getTimeStampAtIndex() {
		return this.hasTimeStampAtIndex;
	}
	
	public String getFILES_url() {
		return FILES_url;
	}

	private void setAndMakeDirectory(String url) {
		directory = Constants.MIRROR_DIR + "\\" + name;
		new File(directory).mkdirs();
	}

	private void setName(String url) {
		this.name = url.split("/")[2];
	}

	public String getDirectory() {
		return directory;
	}

	public String getName() {
		return this.name;
	}

	public String getUrl() {
		return this.url;
	}

	public boolean setTimestamp() {
		LocalDateTime ts = localDateTimeParser(timestamps.TimestampUpdateChecker.getTimestamp(url + "timestamp"));
		boolean changed = false;
		if (ts != null) {
			if(this.timestamp == null || !ts.equals(timestamp)) {
				this.timestamp = ts;
				changed = true;
			}
		}
		else { //some connection problem
			if(this.timestamp != null) changed = true;
		}
		return changed;
	}

	public LocalDateTime getTimestamp() {
		return this.timestamp;
	}

	public static LocalDateTime localDateTimeParser(String timestamp) {
		if (timestamp.equals("no connection"))
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
		LocalDateTime dateTime = LocalDateTime.parse(timestamp, formatter);
		return dateTime;
	}
	
	public static LocalTime localTimeParser(String time, String format) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
		LocalTime localTime = LocalTime.parse(time, formatter);
		return localTime;
		
	}
}
