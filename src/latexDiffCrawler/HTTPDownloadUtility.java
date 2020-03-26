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
		return file.replaceAll("<", "%3e");
	}
	
	public static String replaceBlanks(String file) {
		return file.replaceAll(" ", "%");
	}
	
	public static String replaceNotAllowedCharactersForURL(String file) {
		file = replaceBracket(file);
		file = replaceBlanks(file);
		return file;
	}
	
	public static String replaceNotAllowedCharactersForURI(String file) {
		file = replaceBracket(file);
		char[] notAllowedCharacters = {':', '*', '?', '"', '|'};
		for(char c : notAllowedCharacters) {
			file = file.replace(c, '%');
		}
		if(file.charAt(1) == '%') file = file.replaceFirst("%", ":");
		return file;
	}

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
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return getInputStream(replaceNotAllowedCharactersForURL(fileURL));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return inputStream;
	}

	public static boolean HttpConnectionIsOkay(String fileURL) {
		int responseCode = 0;
		try {
			URL url = new URL(fileURL);
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			responseCode = httpConn.getResponseCode();
			httpConn.disconnect();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return responseCode == HttpURLConnection.HTTP_OK;
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

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return downloadFile(replaceNotAllowedCharactersForURL(fileURL), saveFilePath);
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

	public static String makeDir(String newURI, String file) {
		String pathToMake = "";
		if (file.contains("/")) {
			pathToMake = newURI.substring(0, newURI.lastIndexOf("/"));
			new File(pathToMake).mkdirs();
		}
		return pathToMake;
	}
	
	public static long getHash(String masterURI, String file) {
		long masterChecksum = 0;
		try (InputStream is = Files.newInputStream(Paths.get(masterURI))) {
			masterChecksum = Adler(is);
		} catch (AccessDeniedException e) {
			String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
			makeDir(newURI, file);
			downloadFile(Constants.DANTE + file, newURI);
			return getHash(newURI, file);
		} catch (InvalidPathException e) {
			e.printStackTrace();
			if (file != null) {
				String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				newURI = replaceNotAllowedCharactersForURI(newURI);
				makeDir(newURI, file);
				downloadFile(Constants.DANTE + file, newURI);
				return getHash(newURI, file);
			}
		} catch (NoSuchFileException e) {
			e.printStackTrace();
			if (file != null && masterURI.endsWith(".")) {
				String newURI = Constants.MASTER_DIFFICULT_FILES_DIR + "\\" + file;
				newURI = (String) newURI.subSequence(0, newURI.length() - 1);
				makeDir(newURI, file);
				downloadFile(Constants.DANTE + file, newURI);
				return getHash(newURI, file);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return masterChecksum;
	}

	public static boolean compareHashesForLocalFiles(String masterUri, String mirrorUri) {
		long masterChecksum = getHash(masterUri, null);
		long mirrorChecksum = getHash(mirrorUri, null);
		return masterChecksum == mirrorChecksum;
	}

	public static boolean compareHashesOnline(String masterURI, InputStream mirrorIS) {
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
				equal = compareHashesOnline(Constants.MASTER_DIR + "\\FILES.byname", is);
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return equal;
	}

	public static boolean filesAreEqual(String file, Mirror mirror, long masterChecksum) {
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

}