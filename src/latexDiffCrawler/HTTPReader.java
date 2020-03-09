package latexDiffCrawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;

public class HTTPReader {

	private Mirror mirror;
	private static ArrayList<String> urls = new ArrayList<String>();

	public HTTPReader(Mirror mirror) {
		this.mirror = mirror;
	}

	// returns the string of a file that is found under @url
	public String HttpUrlRequest(String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		return response.body();
	}

	// saves all the links in @file that are directories (ends with "/") to @urls
	// tries to download all the links that are files (when they are different than
	// the master files)
	// adds files to @urls when there are any download errors so that you can try to
	// download them later again
	public void aRound(String file, boolean firstRound, String uri, String mirrorURL) {
		String[] splits = file.split("<a href=\"");
		int k;
		if (firstRound) { // the first directory link at the ctan landing page is the third link (k = 2)
			k = 2;
		} else { // the first directory link deeper the in ctan directory (i.e. not landing page)
					// is the seventh link (k = 6)
			k = 6;
		}
		for (int i = k; i < splits.length; i++) {
			String s = splits[i];
			String t = s.split(">")[0];
			t = t.replaceAll(Matcher.quoteReplacement("\""), "");
			if (!(t.startsWith("http://")) && !(t.startsWith("https://")) && !(t.startsWith("www."))
					&& !(t.contains("mailto:")) && !(t.endsWith(".html"))) {
				String nextUri = uri + t;
				if (t.endsWith("/")) // directory
					urls.add(nextUri);
				else { // file
					boolean downloadError = downloadFile(Constants.DANTE + nextUri, mirrorURL + nextUri, nextUri, t);
					if (downloadError)
						urls.add(nextUri);
					System.out.println(uri + t);
				}
			}
		}
	}

	// returns the responseBody of the HttpUrlRequest for the @url or returns "" if
	// there was an Exception
	public String getBody(String url) {
		String fileToLookAt = "";
		try {
			fileToLookAt = HttpUrlRequest(url);
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileToLookAt;
	}

	// crawls until all the urls in @urls are checked
	public ArrayList<String> crawlAndDownload() {
		String url = mirror.getUrl();
		String fileToLookAt = getBody(url);
		ArrayList<String> files = new ArrayList<String>();
		aRound(fileToLookAt, true, "", url);
		while (!urls.isEmpty()) {
			String next = "";
			int i;
			for (i = 0; i < urls.size(); i++) {
				if (urls.get(i).endsWith("/")) {
					next = urls.get(i);
					break;
				}
			}
			if (next.equals("")) { // all entries were files and there are no directories any more -> finish
				files.addAll(urls);
				urls.removeAll(urls);
			} else {
				String nextURL = url + next;
				fileToLookAt = getBody(nextURL);
				urls.remove(i);
				if (fileToLookAt.isEmpty()) {
					urls.add(nextURL);
					continue;
				}
				aRound(fileToLookAt, false, next, url);
			}
		}
		System.out.println("finish");
		return files;

	}

	// tries to download the files that you can find under the @masterURL and the
	// @mirrorURL
	public boolean downloadFile(String masterURL, String mirrorURL, String file, String fileNameWithoutSlashs) {
		try {
			HTTPDownloadUtility.compareAndDownload(masterURL, mirrorURL, file, mirror.getName(), fileNameWithoutSlashs);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return true;
		}
		return false;
	}

}
