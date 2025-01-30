package ru.rexchange.tools;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtils {
  //todo getExceptionDescription(Throwable t)
  public static String getStackTraceLines(Throwable e, int count) {
    String stacktrace = ExceptionUtils.getStackTrace(e);
    int index = 0;
    while (count >= 0 && index != -1) {
      index = stacktrace.indexOf("\n", index + 1);
      count--;
    }
    return index == -1 ? stacktrace : stacktrace.substring(0, index);
  }

  public static String getStackTrace(Throwable cause) {
    StringWriter res = new StringWriter();
    cause.printStackTrace(new PrintWriter(res));
    return res.toString();
  }

}
