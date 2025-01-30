package ru.rexchange.tools;

public class TradeUtils {
  public interface Direction {
    String SHORT = "SELL";
    String LONG = "BUY";
  }

  public static String getPair(String... pair) {
    return String.format("%s%s", pair[0], pair[1]);
  }

  public static float shiftPriceDown(float rate, float shiftPercent) {
    return rate * (1.f - shiftPercent);
  }

  public static float shiftPriceUp(float rate, float shiftPercent) {
    return rate * (1.f + shiftPercent);
  }
}
