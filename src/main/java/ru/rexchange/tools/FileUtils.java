package ru.rexchange.tools;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class FileUtils {
	public static final String DEFAULT_ENCODING = "UTF-8";
  //public static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_ENCODING);
  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
  public static final String CACHE_DIR = ".cache";

  public static String readToString(InputStream is, String encoding, int maxSize)
			throws IOException {
		char[] buf = new char[4096];

    try (BufferedReader in = new BufferedReader(new InputStreamReader(is, encoding))) {
      StringBuilder sb = new StringBuilder();

      do {
        int c = in.read(buf);
        if (c < 0) {
          return sb.toString();
        }

        sb.append(buf, 0, c);
      } while (maxSize <= 0 || sb.length() < maxSize);

      sb.setLength(maxSize);
      return sb.toString();
    }
	}

	public static String readToString(InputStream is, String encoding) throws IOException {
		return readToString(is, encoding, -1);
	}

  public static synchronized void writeStringToFileSafeSync(String fileName, String data, boolean append) {
    writeStringToFileSafe(fileName, data, append);
  }

  public static void writeStringToFileSafe(String fileName, String data, boolean append) {
    try {
      writeStringToFile(fileName, data, append);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeStringToFile(String fileName, String data, boolean append) throws IOException {
    // Writes a blank file to confirm write process functions as intended
    try (Writer thisFile = new OutputStreamWriter(new FileOutputStream(fileName, append), DEFAULT_CHARSET)) {
      BufferedWriter writer = new BufferedWriter(thisFile);
      writer.write(data);
      writer.close();
    }
  }

  public static String readFileContent(String fileName) {
    try (FileInputStream thisStream = new FileInputStream(fileName)) {
      StringBuilder sb = new StringBuilder();
      try (Scanner thisScanner = new Scanner(thisStream)) {
        while (thisScanner.hasNextLine()) {
          sb.append(thisScanner.nextLine());
          if (thisScanner.hasNextLine())
            sb.append(System.lineSeparator());
        }
      }
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
