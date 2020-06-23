package latexDiffCrawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

public class HTTPDownloadUtility {
	public static final int BUFFER_SIZE = 4096;

	public static String replaceBracket(String file) {
		file = file.replaceAll(">", "%3e");
		return file.replaceAll("<", "%3c");
	}

	public static String replaceBlanks(String file) {
		return file.replaceAll(" ", "%20");
	}

	public static String replacePercentSign(String file) {
		return file.replaceAll("%", "%25");
	}

	// replaces the bad characters in an URL with their ascii code
	public static String replaceNotAllowedCharactersForURL(String file) {
		file = replacePercentSign(file);
		file = replaceBracket(file);
		file = replaceBlanks(file);
		return file;
	}

	// replaces the bad characters in an URI
	public static String replaceNotAllowedCharactersForURI(String file) {
		file = replaceBracket(file);
		char[] notAllowedCharacters = { ':', '*', '?', '"', '|' };
		for (char c : notAllowedCharacters) {
			file = file.replace(c, '%');
		}
		if (file.charAt(1) == '%')
			file = file.replaceFirst("%", ":");
		return file;
	}

	// returns the inputStream from an HttpURLConnection for the specific @fileURL
	// the returned inputStream can later be used for checking the checksum
	// @ASCII_sensitive is true when the @fileURL's bad characters were replaced
	// with their ASCII code
	public static InputStream getInputStream(String fileURL, boolean ASCII_sensitive) {
		InputStream inputStream = null;
		HttpURLConnection httpConn;
		try {
			URL url = new URL(fileURL);
			httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();
			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				inputStream = httpConn.getInputStream();
			} else {
				httpConn.disconnect();
				if (!ASCII_sensitive)
					return getInputStream(replaceNotAllowedCharactersForURL(fileURL), true);
			}
		} catch (IOException e) {
			if (!ASCII_sensitive)
				return getInputStream(replaceNotAllowedCharactersForURL(fileURL), true);
			else
				e.printStackTrace();
		}
		return inputStream;
	}

	/*
	 * downloads a file from @fileURL and saves it to @saveFilePath
	 * 
	 * @ASCII_sensitive is true when the @fileURL's bad characters were replaced
	 * with their ASCII code
	 */
	public static boolean downloadFile(String fileURL, String saveFilePath, boolean ASCII_sensitive) {
		boolean didDownload = false;
		try {
			URL url = new URL(fileURL);
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			int responseCode = httpConn.getResponseCode();

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				didDownload = downloadFile(httpConn, saveFilePath);
			} else {
				System.out.println(
						"File " + fileURL + " couldn't be downloaded. Server replied HTTP code: " + responseCode);
				if (!ASCII_sensitive)
					return downloadFile(replaceNotAllowedCharactersForURL(fileURL), saveFilePath, true);
			}
			httpConn.disconnect();
		} catch (IOException e) {
			if (!ASCII_sensitive)
				return downloadFile(replaceNotAllowedCharactersForURL(fileURL), saveFilePath, true);
			else
				e.printStackTrace();
		}
		return didDownload;
	}

	// downloads a file from @httpConn and saves it to @saveFilePath
	// @httpConn has to be checked by the caller
	public static boolean downloadFile(HttpURLConnection httpConn, String saveFilePath) {
		boolean didDownload = false;
		try {
			// opens an output stream to save into file
			FileOutputStream outputStream = new FileOutputStream(saveFilePath);

			// opens input stream from the HTTP connection
			InputStream inputStream = httpConn.getInputStream();

			int bytesRead = -1;
			byte[] buffer = new byte[BUFFER_SIZE];
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.close();
			inputStream.close();
			didDownload = true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return downloadFile(httpConn, replaceNotAllowedCharactersForURI(saveFilePath));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return didDownload;
	}

	// returns the response body string of a file that is found under @url
	public static String HttpUrlRequest(String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		return response.body();
	}

	// returns all the files that are listed on xxx/FILES.byname
	// if @removeObsolete is true, the files starting with "obsolete/" are excluded
	public static ArrayList<String> getFilesFromFILES(String FILES_url, boolean removeObsolete) {
		String s = "";
		if (FILES_url.endsWith("FILES.byname")) {
			try {
				s = HttpUrlRequest(FILES_url);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (!s.isEmpty()) {
				String[] lines = s.split("\n");
				ArrayList<String> files = new ArrayList<String>();
				for (String st : lines) {
					String fileURI = st.substring(st.lastIndexOf("|") + 2, st.length());
					if (removeObsolete) {
						if (!fileURI.startsWith("obsolete/"))
							files.add(fileURI);
					} else
						files.add(fileURI);
				}
				return files;
			}
		}
		return null;
	}

	// makes a new directory with the @file path and returns that new path
	public static String makeDir(String file) {
		String pathToMake = "";
		if (file.contains("/")) {
			pathToMake = file.substring(0, file.lastIndexOf("/"));
			new File(pathToMake).mkdirs();
		}
		return pathToMake;
	}

	/*
	 * Returns the Adler checksum for the @file that is saved at @masterURI or 0 if
	 * the file could not be opened
	 */
	public static long getHash(String masterURI, String file) {
		long masterChecksum = 0;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = Adler(is);
		} catch (AccessDeniedException e) {
			if (file != null) {
				File masterFile = new File(masterURI);
				String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				if (masterFile.isDirectory())
					newURI = newURI + newURI.charAt(newURI.length() - 1);
				return tryAgain(file, newURI);
			} else
				e.printStackTrace();
		} catch (InvalidPathException e) {
			if (file != null) {
				String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				newURI = replaceNotAllowedCharactersForURI(newURI);
				return tryAgain(file, newURI);
			} else
				e.printStackTrace();
		} catch (NoSuchFileException e) {
			if (file != null && masterURI.endsWith(".")) {
				String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				newURI = (String) newURI.subSequence(0, newURI.length() - 1);
				return tryAgain(file, newURI);
			} else
				e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return masterChecksum;
	}

	/*
	 * returns the Adler checksum for a "difficult" file that could not be opened
	 * due to bad naming with a work around: downloads the file from Dante again and
	 * saves it to the directory for difficult files, so that the checksum can then
	 * be computed from this @newURI path
	 */
	private static long tryAgain(String file, String newURI) {
		if (!Main.difficultFiles.contains(file))
			Main.difficultFiles.add(file);
		makeDir(newURI);
		downloadFile(Constants.DANTE + file, newURI, false);
		return getHash(newURI, file);
	}

	/*
	 * computes both the Adler checksums for the local files under @masterUri
	 * and @mirrorUri and returns true if they are equal, false if not
	 */
	public static boolean compareHashesForLocalFiles(String masterUri, String mirrorUri) {
		long masterChecksum = getHash(masterUri, null);
		long mirrorChecksum = getHash(mirrorUri, null);
		return masterChecksum == mirrorChecksum;
	}

	/*
	 * computes and compares the Adler checksums for the local file under @masterURI
	 * and an InputStream @mirrorIS and returns false if they are not equal and true
	 * if they are
	 */
	public static boolean compareHashesOnline(String masterURI, InputStream mirrorIS) {
		long masterChecksum = 0, slaveChecksum = 0;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = Adler(is);
			slaveChecksum = Adler(mirrorIS);
			is.close();
			return masterChecksum == slaveChecksum;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * Computes the Adler checksum for the InputStream @mirrorIS and compares it
	 * with the
	 * 
	 * @masterChecksum. Returns false if they are not equal and true if they are
	 */
	public static boolean compareHashesOnlineWithMasterHash(long masterChecksum, InputStream mirrorIS) {
		long slaveChecksum = Adler(mirrorIS);
		return masterChecksum == slaveChecksum;
	}

	public static boolean compareFILES_byname(Mirror mirror) {
		InputStream is = HTTPDownloadUtility.getInputStream(mirror.getFILES_url(), false);
		boolean equal = false;
		if (is != null) {
			try {
				equal = compareHashesOnline(Constants.MASTER_DIR + "\\FILES.byname", is);
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return equal;
	}

	/*
	 * Returns true if the Adler checksum for the @file that is uploaded at the
	 * specific @mirror is equal to @masterChecksum
	 */
	public static boolean filesAreEqual(String file, Mirror mirror, long masterChecksum, boolean useMasterChecksum) {
		InputStream is = HTTPDownloadUtility.getInputStream(mirror.getUrl() + file, false);
		boolean equal = false;
		if (is != null) {
			if (useMasterChecksum)
				equal = compareHashesOnlineWithMasterHash(masterChecksum, is);
			else
				equal = compareHashesOnline(Constants.MASTER_DIR + "\\" + file, is);
			try {
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			if(masterChecksum == 0 || getHash(Constants.MASTER_DIR + "\\" + file, null) == 0) {
				// the file probably doesn't exist anymore
				equal = true; 
			}
		}
		return equal;
	}

	// computes the Adler checksum for the InputStream @is
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

}