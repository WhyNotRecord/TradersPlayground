package ru.rexchange.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.io.IOException;

public class TestTools {
  public static final String API_KEY_PROPERTY = "common.api.key";
  public static final String API_SECRET_KEY_PROPERTY = "common.api.secret_key";

  public static boolean prepareEnvironment() throws IOException {
    System.setProperty("log4j.skipJansi", "false");
    configureLogging();
    try {
      /*String propsFile = "config_test.json";
      if (!new File(propsFile).exists()) {
        LogManager.getLogger(TestTools.class).warn("Test config file's not found");
        return false;
      }*/
      if (System.getenv().containsKey(API_KEY_PROPERTY)) {
        System.setProperty(API_KEY_PROPERTY, System.getenv(API_KEY_PROPERTY));
      } else {
        System.setProperty(API_KEY_PROPERTY, "<api_key>");
      }
      if (System.getenv().containsKey(API_SECRET_KEY_PROPERTY)) {
        System.setProperty(API_SECRET_KEY_PROPERTY, System.getenv(API_SECRET_KEY_PROPERTY));
      } else {
        System.setProperty(API_SECRET_KEY_PROPERTY, "<api_secret>");
      }
    } catch (Exception e) {
      LogManager.getLogger(TestTools.class).error("Can't load test config", e);
      return false;
    }
    return true;
  }

  public static void configureLogging() {
    // import org.apache.logging.log4j.core.LoggerContext;
    LoggerContext context = (LoggerContext)
        LogManager.getContext(false);
    File file = new File("log4j2-test.xml");

    // this will force a reconfiguration
    context.setConfigLocation(file.toURI());
  }
}
