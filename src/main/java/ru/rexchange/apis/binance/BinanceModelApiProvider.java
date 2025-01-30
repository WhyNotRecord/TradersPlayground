package ru.rexchange.apis.binance;

import com.binance.connector.client.impl.SpotClientImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.tools.TradeUtils;
import ru.rexchange.tools.Utils;

import java.util.*;

public class BinanceModelApiProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(BinanceModelApiProvider.class);
		/*
		private static final String API_SECRET_KEY = "12345";
		private static final String API_TOKEN = "00000000-11111111-22222222-33333333-44444444";
		*/

  private static final SpotClientImpl client = new SpotClientImpl();
  /*todo влияет на структуру ответа, получаемого от API, ломает парсинг static {
    client.setShowLimitUsage(true);
  }*/
  public static Double getPrice(String[] pair) {
    for (int i = 0; i < pair.length; i++) {
      if ("USD".equalsIgnoreCase(pair[i])) {
        pair[i] = "USDT";
      }
    }
    return getPrice(TradeUtils.getPair(pair));
  }

  @Nullable
  public static Double getPrice(String symbol) {
    LinkedHashMap<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("symbol", symbol);
    String result = client.createMarket().ticker(params);
    try {
      Gson gson = new Gson();
      CandleResponse cont = gson.fromJson(result, CandleResponse.class);
      return cont.lastPrice;
    } catch (JsonSyntaxException e) {
      LOGGER.error(String.format("Ошибка разбора ответа:%n%s", result), e);
      return null;
    }
  }

  //TODO продумать механизм синхронизации, чтобы не быть забаненым за активный DDOS API
  //https://www.binance.com/en/support/faq/api-frequently-asked-questions-360004492232
  public static CandleResponse getLastCandle(String[] pair, String period) {
    for (int i = 0; i < pair.length; i++) {
      if ("USD".equalsIgnoreCase(pair[i])) {
        pair[i] = "USDT";
      }
    }
    LinkedHashMap<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("symbol", TradeUtils.getPair(pair));
    params.put("type", "MINI");
    if (period != null)
      params.put("windowSize", period);
    String result = client.createMarket().ticker(params);
    try {
      Gson gson = new Gson();
      return gson.fromJson(result, CandleResponse.class);
    } catch (JsonSyntaxException e) {
      LOGGER.error(String.format("Ошибка разбора ответа:%n%s", result), e);
      return null;
    }
  }

  public static CandleResponse getLastCandle(String[] pair, String period, int attempts) {
    long to = 500L;
    for (int i = 1; i < attempts; i++) {
      try {
        return getLastCandle(pair, period);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to, LOGGER);
        to *= 2L;
      }
    }
    return getLastCandle(pair, period);
  }

  public static List<CandleData> getLastCandles(String[] pair, String period, int count, long startTime) {
    for (int i = 0; i < pair.length; i++) {
      if ("USD".equalsIgnoreCase(pair[i])) {
        pair[i] = "USDT";
      }
    }
    LinkedHashMap<String, Object> params = new LinkedHashMap<>();
    params.put("symbol", TradeUtils.getPair(pair));
    params.put("interval", period);
    params.put("limit", count);
    params.put("startTime", startTime);
    //params.put("endTime", endTime);
    String response = client.createMarket().klines(params);
    try {
      //JsonParser parser = new JsonParser();
      JsonArray array = JsonParser.parseString(response).getAsJsonArray();
      Gson gson = new Gson();
      int arrSize = array.size();
      List<CandleData> result = new ArrayList<>();
      for (int i = 0; i < arrSize; i++) {
        String[] cont = gson.fromJson(array.get(i), String[].class);
        CandleData cd = new CandleData(cont);
        result.add(cd);
      }
      return result;
    } catch (JsonSyntaxException e) {
      LOGGER.error(String.format("Ошибка разбора ответа:%n%s", response), e);
      return null;
    }
  }

  public static List<CandleData> getLastCandles(String[] pair, String period, int count, long startTime, int attempts) {
    long to = 500L;
    for (int i = 1; i < attempts; i++) {
      try {
        return getLastCandles(pair, period, count, startTime);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to, LOGGER);
        to *= 2L;
      }
    }
    return getLastCandles(pair, period, count, startTime);
  }

  public static class CandleData {
    public long openTime;
    public double openPrice;
    public double highPrice;
    public double lowPrice;
    public double closePrice;
    public double volume;
    public long closeTime;
    public double quotedVolume;
    public long trades;
    public double takerBuyBase;
    public double takerBuyQuote;

    public CandleData(String[] data) {
      openTime = Long.parseLong(data[0]);
      openPrice = Double.parseDouble(data[1]);
      closePrice = Double.parseDouble(data[4]);
      closeTime = Long.parseLong(data[6]);
      volume = Double.parseDouble(data[5]);
      quotedVolume = Double.parseDouble(data[7]);
      lowPrice = Double.parseDouble(data[3]);
      highPrice = Double.parseDouble(data[2]);
    }

    public String toString() {
      return String.format("Time: %s. Low: %s. High: %s. Open: %s. Close: %s. Volume: %s. Quoted vol: %s",
          new Date(openTime), lowPrice, highPrice, openPrice, closePrice, volume, quotedVolume);
    }

    public static String[] getColumnsHeader() {
      return new String[]{"Time", "Low", "High", "Open", "Close", "Volume", "Quoted vol"};
    }

    public String toCSVString() {
      return String.format(Locale.ROOT, "%s, %.2f, %.2f, %.2f, %.2f, %.1f, %.2f",
          openTime, lowPrice, highPrice, openPrice, closePrice, volume, quotedVolume);
    }
  }


  /*public static class PriceResponse {
    public double mins;
    public double price;
  }*/

  public static class CandleResponse {
    String symbol;
    public double openPrice;
    public double highPrice;
    public double lowPrice;
    public double lastPrice;
    public double volume;
    public double quoteVolume;
    public long openTime;
    public long closeTime;

    public String toString() {
      return String.format("Time: %s. Low: %s. High: %s. Open: %s. Close: %s. Volume: %s. Quoted vol: %s",
          new Date(openTime), lowPrice, highPrice, openPrice, lastPrice, volume, quoteVolume);
    }
  }
}