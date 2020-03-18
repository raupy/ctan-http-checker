package latexDiffCrawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

import org.apache.commons.codec.digest.DigestUtils;

public class HTTPDownloadUtility {
	public static final int BUFFER_SIZE = 4096;

	// returns the inputStream from an HttpURLConnection for the specific @fileURL
	// the returned inputStream can later be used for checking the checksum
	public static InputStream getInputStream(String fileURL) {
		InputStream inputStream = null;
		HttpURLConnection httpConn;
		try {
			URL url = new URL(fileURL);
			httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();
			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				inputStream = httpConn.getInputStream();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return inputStream;
	}

	public static boolean downloadFile(String fileURL, String saveFilePath) {
		boolean didDownload = false;
		try {
			URL url = new URL(fileURL);
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String fileName = "";
				int contentLength = httpConn.getContentLength();
				// extracts file name from URL
				fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
				System.out.println("Content-Length = " + contentLength);
				System.out.println("fileName = " + fileName);
				didDownload = downloadFile(httpConn, saveFilePath);
			} else {
				System.out.println("No file to download. Server replied HTTP code: " + responseCode);
			}
			httpConn.disconnect();

		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return didDownload;
	}

	// downloads a file from @httpConn and saves it to @saveFilePath
	// @httpConn has to be checked by the caller
	public static boolean downloadFile(HttpURLConnection httpConn, String saveFilePath) {
		boolean didDownload = false;
		try {
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
			didDownload = true;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return didDownload;
	}

	// returns the string of a file that is found under @url
	public static String HttpUrlRequest(String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		return response.body();
	}

	// returns all the files that are listed on xxx/FILES.byname
	public static ArrayList<String> getFilesFromFILES(String FILES_url, boolean removeObsolete) {
		String s = "";
		if (FILES_url.endsWith("FILES.byname")) {
			try {
				s = HttpUrlRequest(FILES_url);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!s.isEmpty()) {
				String[] lines = s.split("\n");
				ArrayList<String> files = new ArrayList<String>();
				for (String st : lines) {
					String fileURI = st.substring(st.lastIndexOf("|") + 2, st.length());
					if(removeObsolete) {
						if(!fileURI.startsWith("obsolete/")) files.add(fileURI);
					}
					else files.add(fileURI);
				}
				return files;
			}
		}
		return null;
	}


	public static boolean compareHashesOnline(String masterURI, InputStream mirrorIS) throws IOException {
		String masterMD5;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterMD5 = DigestUtils.md5Hex(is);
		}
		String slaveMD5;
		slaveMD5 = DigestUtils.md5Hex(mirrorIS);
		return masterMD5.equals(slaveMD5);
	}

	public static String getStringHash(String masterURI) {
		String masterChecksum = null;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = DigestUtils.md5Hex(is);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return masterChecksum;
	}
	
	public static long getHash(String masterURI) {
		long masterChecksum = 0;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = Adler(is);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return masterChecksum;
	}
	
	public static boolean compareHashesForLocalFiles(String masterUri, String mirrorUri) {
		long masterChecksum = getHash(masterUri);
		long mirrorChecksum = getHash(mirrorUri);
		return masterChecksum == mirrorChecksum;
	}

	public static boolean compareHashesOnline2(String masterURI, InputStream mirrorIS) {
		long masterChecksum = 0, slaveChecksum = 0;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = Adler(is);
			slaveChecksum = Adler(mirrorIS);
			if (masterChecksum != 0 && slaveChecksum != 0)
				return masterChecksum == slaveChecksum;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	public static boolean compareHashesOnlineWithMasterHash(long masterChecksum, InputStream mirrorIS) {
		long slaveChecksum = Adler(mirrorIS);
		if (masterChecksum != 0 && slaveChecksum != 0)
			return masterChecksum == slaveChecksum;
		return false;
	}

	public static boolean compareFILES_byname(Mirror mirror) {
		InputStream is = HTTPDownloadUtility.getInputStream(mirror.getFILES_url());
		boolean equal = false;
		if (is != null) {
			try {
				equal = compareHashesOnline2(Constants.MASTER_DIR + "\\FILES.byname", is);
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return equal;
	}

	public static boolean filesAreEqual(String file, Mirror mirror) {
		InputStream is = HTTPDownloadUtility.getInputStream(mirror.getUrl() + file);
		boolean equal = false;
		if (is != null) {
			try {
				equal = compareHashesOnline2(Constants.MASTER_DIR + "\\" + file, is);
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return equal;
	}
	
	public static boolean filesAreEqual2(String file, Mirror mirror, long masterChecksum) {
		InputStream is = HTTPDownloadUtility.getInputStream(mirror.getUrl() + file);
		boolean equal = false;
		if (is != null) {
			try {
				equal = compareHashesOnlineWithMasterHash(masterChecksum, is);
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return equal;
	}

	public static long Adler(InputStream is) {
		long checksum = 0;
		try {
			Adler32 adlerChecksum = new Adler32();
			CheckedInputStream cinStream = new CheckedInputStream(is, adlerChecksum);
			byte[] b = new byte[HTTPDownloadUtility.BUFFER_SIZE];
			while (cinStream.read(b) >= 0) {
			}
			checksum = cinStream.getChecksum().getValue();
			cinStream.close();
		} catch (IOException e) {
			System.out.println("IOException : " + e);
		}
		return checksum;
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

			if (responseCode == HttpURLConnection.HTTP_OK /*
															 * && !equalContentLengths(masterConnection,
															 * mirrorConnection)
															 */) {
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