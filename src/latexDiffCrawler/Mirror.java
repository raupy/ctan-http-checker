package latexDiffCrawler;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/*
 * This class implements a CTAN mirror that supports HTTP / HTTPS.  
 */
public class Mirror {

	// attributes that are retrieved from the mirror's @url
	private String url;
	private String name;
	private String directory;
	private String FILES_url;
	private LocalDateTime timestamp;

	// attributes that are retrieved from the mirrorData.txt file

	private boolean fixedTS = false;
	// does this mirror have fixed time stamps (like e.g. 07:02)
	// XOR fixed update times like e.g. every day at 10:36 o'clock

	private ArrayList<Float> hasTimeStampAtIndex = new ArrayList<Float>();
	/*
	 * 24 elements for 24 hours, every element is either 0 (no time stamp there) 1
	 * (yes there is a time stamp at this hour) 0.5 (maybe, maybe not; not regular)
	 */

	private int prio;
	// mirrors that have more entries in @hasTimeStampAtIndex will have a lower
	// priority
	// because they can be compared more often again

	private Hashtable<Integer, LocalTime> timeStampRealTimeRelation;
	// it's the releation between the time stamps from @hasTimeStampAtIndex and the
	// "real" update time when the files with the specific time stamp were uploaded

	public Mirror(String url) {
		this.url = url;
		setName(url);
		if (!url.contains(Constants.DANTE))
			setAndMakeDirectory(url);
		FILES_url = url + "FILES.byname";
	}

	/*
	 * All the data in the mirrorData.txt file was recorded with an Master offset
	 * (difference between the master time stamp and the real time) of -1. This
	 * offset can change (and it did, right now (3. April 2020) it is -2). Off
	 * course, then the mirror attributes aren't correct anymore, that's why this
	 * method will calculate new attributes.
	 */
	public void notifyOffsetChanged(int offset, int newOffset) {
		int diff = offset - newOffset;
		changeRelation(diff);
		if (fixedTS) {
			// the mirror has a fixed time stamp, so @hasTimeStampAtIndex has to remain the
			// same
			// we just have to change the @timeStampRealTimeRelation
		} else {
			if (diff > 0) { // positive -> push array left
				while (diff > 0) {
					float f = this.hasTimeStampAtIndex.remove(0);
					this.hasTimeStampAtIndex.add(f);
					diff--;
				}
			} else { // negative -> push array right
				while (diff < 0) {
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

	/*
	 * called when the master offset changed and the relation between the recorded
	 * time stamps and the real time updates has to be adjusted
	 */
	private void changeRelation(int diff) {
		if (fixedTS) { // the key remains the same, the LocalTime value has to change
			for (Integer key : this.getTimeStampRealTimeRelation().keySet())
				this.timeStampRealTimeRelation.put(key, this.timeStampRealTimeRelation.get(key).plusHours(diff));
		} else { // the LocalTime value remains the same, the key has to change
			Hashtable<Integer, LocalTime> newMap = new Hashtable<Integer, LocalTime>();
			for (Integer i : this.timeStampRealTimeRelation.keySet()) {
				LocalTime value = this.timeStampRealTimeRelation.get(i);
				if (i == 0 && diff > 0)
					i = 24;
				else if (i == 23 && diff < 0)
					i = -1;
				newMap.put(i - diff, value);
			}
			this.timeStampRealTimeRelation = newMap;
		}
	}

	// sets the @fixedTS attribute
	private void setTS(String trueOrFalse) {
		if (trueOrFalse.equals("true"))
			fixedTS = true;
	}

	// sets all the attributes using the information retrieved from the
	// mirrorData.txt file
	public void setTimeStampInfos(String timestamps, String updates, String trueOrFalse) {
		setTimeStampAtIndex(timestamps);
		setTimeStampRealTimeRelation(updates);
		setPrio();
		setTS(trueOrFalse);
	}

	// puts the time stamps from @hasTimeStampAtIndex in relation with the real time
	// @updates looking like e.g 06:00,12:00,18:00,00:00
	// (they have to be in the same order as in @hasTimeStampAtIndex)
	private void setTimeStampRealTimeRelation(String updates) {
		timeStampRealTimeRelation = new Hashtable<Integer, LocalTime>();
		String[] splittedUpdates = updates.split(",");
		int j = 0;
		for (int i = 0; i < hasTimeStampAtIndex.size(); i++) {
			if (hasTimeStampAtIndex.get(i) > 0) {
				timeStampRealTimeRelation.put(i, localTimeParser(splittedUpdates[j], "HH:mm"));
				j++;
			}
		}
	}

	public Hashtable<Integer, LocalTime> getTimeStampRealTimeRelation() {
		return this.timeStampRealTimeRelation;
	}

	public int getPrio() {
		return prio;
	}

	/*
	 * Calculates the priority for the mirror by simply adding all the elements
	 * from @hasTimeStampAtIndex and diving by 24. That's how mirrors with more time
	 * stamp entries will gain a lower priority for the comparing process
	 */
	private void setPrio() {
		float updatesPerDay = 0;
		for (float i : hasTimeStampAtIndex) {
			updatesPerDay += i;
		}
		prio = 24 / (int) updatesPerDay;
	}

	/*
	 * simply transfers the @timestamps String (looks e.g. like
	 * 0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0) to the @hasTimeStampAtIndex
	 * attribute
	 */
	private void setTimeStampAtIndex(String timestamps) {
		String[] splittedTimestamps = timestamps.split(",");
		for (String ts : splittedTimestamps) {
			if (ts.equals("0"))
				hasTimeStampAtIndex.add(0f);
			else if (ts.equals("0.5"))
				hasTimeStampAtIndex.add(0.5f);
			else
				hasTimeStampAtIndex.add(1f);
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
		this.name = url.split("/")[2]; // http://
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

	// sets the current time stamp for this mirror
	// returns true if the time stamp changed since the last call of the method
	public boolean setTimestamp() {
		LocalDateTime ts = localDateTimeParser(timestamps.TimestampUpdateChecker.getTimestamp(url + "timestamp"));
		boolean changed = false;
		if (ts != null) {
			if (this.timestamp == null || !ts.equals(timestamp)) {
				this.timestamp = ts;
				changed = true;
			}
		} else { // some connection problem
			if (this.timestamp != null)
				changed = true;
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
