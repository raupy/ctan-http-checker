package latexDiffCrawler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Mirror {

	private String url;
	private String name;
	
	public Mirror(String url) {
//		if(url.endsWith("/")) {
//			url = url.substring(0, url.length() - 1);
//		}
		this.url = url;
		splitUrl(url);
	}
	
	private void splitUrl(String url) {
		this.name = url.split("/")[2];
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getUrl() {
		return this.url;
	}
	
	public LocalDateTime getTimestamp() {
		return localDateTimeParser(timestamps.TimestampUpdateChecker.getTimestamp(url + "timestamp"));
	}
	
	public static LocalDateTime localDateTimeParser(String timestamp) {
		if(timestamp.equals("no connection")) return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
		LocalDateTime dateTime = LocalDateTime.parse(timestamp, formatter);
		return dateTime;
	}
}
