package ru.rexchange.apis.kucoin;

import com.kucoin.futures.core.KucoinFuturesClientBuilder;
import com.kucoin.futures.core.KucoinFuturesPublicWSClient;
import com.kucoin.futures.core.KucoinFuturesRestClient;
import com.kucoin.futures.core.rest.response.TickerResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.rexchange.exception.SystemException;
import ru.rexchange.tools.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class KucoinFuturesModelApiProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(KucoinFuturesModelApiProvider.class);
  public static final String KUCOIN_FUTURES_API_URL = "https://api-futures.kucoin.com";
  private static KucoinFuturesRestClient apiClient = null;
  private static KucoinFuturesPublicWSClient apiWsClient = null;

  protected static KucoinFuturesRestClient getClient() {
    if (apiClient == null) {
      KucoinFuturesClientBuilder builder = new KucoinFuturesClientBuilder().withBaseUrl(KUCOIN_FUTURES_API_URL);
      apiClient = builder.buildRestClient();
    }
    return apiClient;
  }

  public static KucoinFuturesPublicWSClient getWsClient() throws IOException {
    if (apiWsClient == null) {
      KucoinFuturesClientBuilder builder = new KucoinFuturesClientBuilder().withBaseUrl(KUCOIN_FUTURES_API_URL);
      apiWsClient = builder.buildPublicWSClient();
    }
    return apiWsClient;
  }

  public static Double getPrice(String[] pair) {
    String symbol = evaluateSymbol(pair);
    try {
      TickerResponse result = getClient().tickerAPI().getTicker(symbol);
      return result.getPrice().doubleValue();
    } catch (Exception e) {
      LOGGER.error("Pair {} price request error", symbol, e);
      return null;
    }
  }

  //todo продумать механизм синхронизации, чтобы не быть забаненым за активный DDOS API
  public static List<String> getLastCandle(String[] pair, int period) throws Exception {
    String symbol = evaluateSymbol(pair);
    long now = TimeUtils.getCurrentPeriodStart(period * 60 * 1000L);
    long start = now - period * 60 * 1000L;
    LOGGER.trace("Requesting last candles with params: {}, {}, {}, {}", symbol, period, start, now);
    List<List<String>> result = getClient().kChartAPI().getKChart(symbol, period, start - 1, now - 1);
    if (result.isEmpty() || result.size() > 2)
      throw new SystemException("Unexpected number of data items: " + result.size());
    Optional<List<String>> requestedCandle =
        result.stream().filter(strings -> String.valueOf(start).equals(strings.get(0))).findAny();
    return requestedCandle.orElse(null);
  }

  public static List<String> getLastCandle(String[] pair, int period, int attempts) {
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

  public static List<List<String>> getLastCandles(String[] pair, int period, int count, long startTime) throws Exception {
    String symbol = evaluateSymbol(pair);
    //long start = TimeUtils.getMomentNPeriodsAgo(period * 60 * 1000L, count, DateUtils.currentTimeMillis());
    long endTime = startTime + period * 60 * 1000L * count - 1;

    return getClient().kChartAPI().getKChart(symbol, period, startTime, endTime);
  }

  public static List<List<String>> getLastCandles(String[] pair, int period, int count, long startTime, int attempts) {
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

  @NotNull
  public static String evaluateSymbol(String[] pair) {
    for (int i = 0; i < pair.length; i++) {
      if ("USD".equalsIgnoreCase(pair[i])) {
        pair[i] = "USDT";
      } else if ("BTC".equalsIgnoreCase(pair[i])) {
        pair[i] = "XBT";
      }
    }
    return TradeUtils.getPair(pair) + "M";
  }

  public static void main(String[] args) throws Exception {
    List<String> candle = getLastCandle(new String[] {"TON", "USDT"}, 60);
    System.out.println(StringUtils.toStringCommon(candle));

    int period = 60 * 4, count = 100;
    long periodMs = period * 60 * 1000L;
    long startTime = TimeUtils.getMomentNPeriodsAgo(periodMs, count);

    List<List<String>> candles = getLastCandles(new String[] {"BNB", "USD"}, period, count, startTime);
    System.out.println(candles.size());
    if (!candles.isEmpty())
      System.out.println(candles.get(candles.size() - 1));
  }
}
