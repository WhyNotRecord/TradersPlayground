package ru.rexchange.tools;

import org.slf4j.Logger;
import ru.rexchange.data.Consts;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

public class LoggingUtils {
  public static final String CLOSED_DEALS_TXT = "CLOSED_DEALS.txt";
  public static final String DEALS_TXT = "DEALS.txt";
  public static final String FILTERED_DEALS_TXT = "FILTERED_DEALS.txt";
  private static boolean notifyTgAboutErrors = false;

  public static void logError(Logger logger, Throwable e, String message, Object... args) {
    String errorText = String.format(message, args) + System.lineSeparator() + e.getMessage();
    logger.error(errorText, e);
  }

  public static synchronized void logDealInfo(String direction, String trader, String strategy, String filter,
                                              String baseCurrency, String quotedCurrency,
                                              Long dealTimeStamp, float dealRate, Float takeProfit, Float stopLoss, Logger LOGGER) {
    try {
      if (!new File(DEALS_TXT).exists()) {//todo write filter also
        String header = CsvTools.toCSVLine(new String[] { "open_time", "trader", "strategy", "direction",
            "base_cur", "quoted_cur", "price", "take-profit", "stop-loss", "result" }, ';');
        FileUtils.writeStringToFile(DEALS_TXT, header + System.lineSeparator(), true);
      }
    } catch (IOException e) {
      LOGGER.warn("Error occurred while writing header to deals log file", e);
    }

    try {
      String line = CsvTools.toCSVLine(new String[] {
          DateUtils.formatTimeMin(dealTimeStamp == null ? DateUtils.currentTimeMillis() : dealTimeStamp),
          trader, strategy, direction, baseCurrency, quotedCurrency,
          String.valueOf(dealRate), String.valueOf(takeProfit), String.valueOf(stopLoss), ""}, ';');
      FileUtils.writeStringToFile(DEALS_TXT, line + System.lineSeparator(), true);
    } catch (Exception e) {
      LOGGER.warn("Error occurred while saving opened deal info", e);
    }
  }

  public static synchronized boolean logDealCloseInfo(String traderName, Long baseOrderTime, Long stopOrderTime,
                                                    String baseCurrency, String quotedCurrency,
                                                    float openRate, float closeRate, BigDecimal amount, String direction,
                                                    float profit, String comment, Logger LOGGER) {
    try {//todo реализовать возможность сохранения в памяти с целью выведения статистики по сделкам бэктестинга
      if (!new File(CLOSED_DEALS_TXT).exists()) {
        String header = CsvTools.toCSVLine(new String[] { "trader", "open_time", "close_time", "direction",
            "base_cur", "quoted_cur", "open_price", "amount", "close_price", "profit", "comment" }, ';');
        FileUtils.writeStringToFile(CLOSED_DEALS_TXT, header + System.lineSeparator(), true);
      }
    } catch (IOException e) {
      LOGGER.warn("Error occurred while writing header to closed deals log file", e);
    }
    try {
      String line = CsvTools.toCSVLine(new String[] { traderName,
          DateUtils.formatTimeMin(baseOrderTime), DateUtils.formatTimeMin(stopOrderTime),
          direction, baseCurrency, quotedCurrency,
          String.valueOf(openRate), amount.toString(),
          String.valueOf(closeRate), String.format("%.2f", profit), comment},
          ';');
      FileUtils.writeStringToFile(CLOSED_DEALS_TXT, line + System.lineSeparator(), true);
      return true;
    } catch (Exception e) {
      LOGGER.warn("Error occurred while saving opened deal info", e);
      return false;
    }
  }

  public static void logFilteredDealInfo(long dealTimeStamp, boolean buy, String bot, String strategy, String pair, String filter,
                                         float rate, float tpPercent, float slPercent, String description, Logger LOGGER) {
    try {
      if (!new File(FILTERED_DEALS_TXT).exists()) {
        String header = CsvTools.toCSVLine(new String[] { "time", "bot", "side", "strategy", "pair",
            "filter", "price", "take-profit", "stop-loss", "description" }, ';');
        FileUtils.writeStringToFile(FILTERED_DEALS_TXT, header + System.lineSeparator(), true);
      }
    } catch (IOException e) {
      LOGGER.warn("Error occurred while writing header to filtered deals log file", e);
    }

    try {
      float takeProfit, stopLoss;
      if (buy) {
        takeProfit = (1.f + tpPercent) * rate;
        stopLoss = (1.f - slPercent) * rate;
      } else {
        takeProfit = (1.f - tpPercent) * rate;
        stopLoss = (1.f + slPercent) * rate;
      }
      String line = CsvTools.toCSVLine(new String[] { DateUtils.formatTimeMin(dealTimeStamp),
          bot, buy ? "BUY" : "SELL", strategy, pair, filter, String.valueOf(rate),
          String.valueOf(takeProfit), String.valueOf(stopLoss), description}, ';');
      FileUtils.writeStringToFile(FILTERED_DEALS_TXT, line + System.lineSeparator(), true);
    } catch (Exception e) {
      LOGGER.warn("Error occurred while saving filtered deal info", e);
    }
  }
}
