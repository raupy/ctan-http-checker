package latexDiffCrawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HTTPDownloadUtility {
	private static final int BUFFER_SIZE = 4096;

	// downloads a file from @httpConn and saves it to @saveFilePath
	// @httpConn has to be checked by the caller
	private static void downloadFile(HttpURLConnection httpConn, String saveFilePath) throws IOException {
		// opens input stream from the HTTP connection
		InputStream inputStream = httpConn.getInputStream();
		// opens an output stream to save into file
		FileOutputStream outputStream = new FileOutputStream(saveFilePath);

		int bytesRead = -1;
		byte[] buffer = new byte[BUFFER_SIZE];
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}
		outputStream.close();
		inputStream.close();
	}

	// returns true if the contentLengths of the two HttpURLConnections are equal
	public static boolean equalContentLengths(HttpURLConnection masterConnection, HttpURLConnection mirrorConnection) {
		int contentLength = masterConnection.getContentLength();
		int contentLength2 = mirrorConnection.getContentLength();
		return contentLength == contentLength2;
	}

	// opens two HttpURLConnections for @masterURLString and @mirrorURLString
	// downloads the files from the HttpURLConnections if their contentLengths are
	// different
	public static void compareAndDownload(String masterURLString, String mirrorURLString, String mirrorURI,
			String mirrorName, String fileNameWithoutSlashs) throws IOException {
		URL url = new URL(masterURLString);
		HttpURLConnection masterConnection = (HttpURLConnection) url.openConnection();
		int responseCode = masterConnection.getResponseCode();

		// always check HTTP response code first
		if (responseCode == HttpURLConnection.HTTP_OK) {
			URL mirrorURL = new URL(mirrorURLString);
			HttpURLConnection mirrorConnection = (HttpURLConnection) mirrorURL.openConnection();
			responseCode = mirrorConnection.getResponseCode();

			if (responseCode == HttpURLConnection.HTTP_OK && !equalContentLengths(masterConnection, mirrorConnection)) {
				// not equal files -> download them
				String mirrorFilePath = Constants.MIRROR_DIR + "\\" + mirrorName + "\\" + mirrorURI;
				String pathToMake = "";
				if (mirrorURI.contains("/"))
					pathToMake = mirrorFilePath.substring(0, mirrorFilePath.lastIndexOf("/"));
				else
					pathToMake = Constants.MIRROR_DIR + "\\" + mirrorName;
				new File(pathToMake).mkdirs();
				downloadFile(masterConnection, pathToMake + "\\master_" + fileNameWithoutSlashs);
				downloadFile(mirrorConnection, mirrorFilePath);
			}
			mirrorConnection.disconnect();
		}
		masterConnection.disconnect();
	}

}