package ru.rexchange.apis.binance;

import binance.futures.enums.IntervalType;
import binance.futures.impl.UnsignedClient;
import binance.futures.model.Candle;
import binance.futures.model.PremiumIndex;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.tools.*;

import java.util.List;
import java.util.Optional;

public class BinanceFuturesModelApiProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(BinanceFuturesModelApiProvider.class);

  public static Double getPrice(String[] pair) {
    String symbol = evaluateSymbol(pair);
    try {
      List<PremiumIndex> result = UnsignedClient.getPremiumIndex(symbol);
      return result.get(0).getIndexPrice().doubleValue();
    } catch (Exception e) {
      LOGGER.error(String.format("Pair price request error %s", symbol), e);
      return null;
    }
  }

  //TODO продумать механизм синхронизации, чтобы не быть забаненым за активный DDOS API
  //https://www.binance.com/en/support/faq/api-frequently-asked-questions-360004492232
  public static Candle getLastCandle(String[] pair, int period) throws Exception {
    String symbol = evaluateSymbol(pair);
    long now = TimeUtils.getCurrentPeriodStart(period * 1000L, DateUtils.currentTimeMillis());
    long start = now - period * 1000L;
    List<Candle> result = UnsignedClient.getKlines(symbol, getIntervalType(period), 1, start);
    Optional<Candle> requestedCandle =
        result.stream().filter(strings -> start == strings.getOpenTime()).findAny();
    return requestedCandle.orElse(null);
  }

  @NotNull
  public static IntervalType getIntervalType(String periodSymbol) {
    return IntervalType.valueOf("_" + periodSymbol);
  }

  public static IntervalType getIntervalType(int period) {
    return IntervalType.valueOf("_" + getPeriodSymbol(period));
  }

  public static Candle getLastCandle(String[] pair, int period, int attempts) {
    long to = 500L;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try {
        return getLastCandle(pair, period);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to, LOGGER);
        to *= 2L;
        last = e;
      }
    }
    if (last != null)
      LOGGER.warn("Last query error:", last);
    return null;
  }

  public static List<Candle> getLastCandles(String[] pair, int period, int count, long startTime) throws Exception {
    String symbol = evaluateSymbol(pair);
    return UnsignedClient.getKlines(symbol, getIntervalType(period), count, startTime);
  }

  public static List<Candle> getLastCandles(String[] pair, int period, int count, long startTime, int attempts) {
    long to = 500L;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try {
        return getLastCandles(pair, period, count, startTime);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to, LOGGER);
        to *= 2L;
        last = e;
      }
    }
    if (last != null)
      LOGGER.warn("Last query error:", last);
    return null;
  }

  public static long getServerTime() throws Exception {
    return UnsignedClient.getServerTime();
  }

  public static long getServerTime(int attempts) {
    long to = 500L;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try {
        return getServerTime();
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to, LOGGER);
        to *= 2L;
        last = e;
      }
    }
    if (last != null)
      LOGGER.warn("Last query error:", last);
    return 0L;
  }

  private static String evaluateSymbol(String[] pair) {
    for (int i = 0; i < pair.length; i++) {
      if ("USD".equalsIgnoreCase(pair[i])) {
        pair[i] = "USDT";
      }
    }
    return TradeUtils.getPair(pair);
  }

  public static String getPeriodSymbol(int period) {
    return TimeUtils.getPeriodSymbol(period);
  }

  public static void main(String[] args) throws Exception {
    Candle candle = getLastCandle(new String[] {"BTC", "USDT"}, 3600);
    System.out.println(candle);

    int period = 3600 * 4, count = 100;
    long periodMs = period * 1000L;
    long startTime = TimeUtils.getMomentNPeriodsAgo(periodMs, count);

    List<Candle> candles = getLastCandles(new String[] {"BNB", "USD"}, period, count, startTime);
    System.out.println(candles.size());
    if (!candles.isEmpty())
      System.out.println(candles.get(candles.size() - 1));
  }
}
