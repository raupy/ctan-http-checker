package latexDiffCrawler;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Mirror {

	private String url;
	private String name;
	private String directory;
	private String FILES_url;
	private LocalDateTime timestamp;
	
	//TODO:
//	private boolean wasChecked = false;
//	private boolean[] hasTimeStampAtIndex;

	public Mirror(String url) {
		this.url = url;
		setName(url);
		setAndMakeDirectory(url);
		FILES_url = url + "FILES.byname";
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
}
