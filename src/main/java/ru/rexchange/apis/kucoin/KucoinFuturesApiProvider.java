package ru.rexchange.apis.kucoin;

import com.kucoin.futures.core.KucoinFuturesClientBuilder;
import com.kucoin.futures.core.KucoinFuturesRestClient;
import com.kucoin.futures.core.rest.response.ContractResponse;
import com.kucoin.futures.core.rest.response.TickerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.tools.Utils;

import java.util.List;

public class KucoinFuturesApiProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(KucoinFuturesApiProvider.class);
  public static final long EXCHANGE_INFO_CACHE_LIVE_TIME = 60 * 60 * 1000L;
  public static final int REQUEST_ATTEMPTS_COUNT = 3;
  public static final long FAILED_REQUEST_REPEAT_PAUSE = 500L;
  private static KucoinFuturesRestClient restClient = null;

  /*public static boolean canTrade(KucoinSignedClient client) throws Exception {
      return client.canTrade();
  }*/

  private static synchronized KucoinFuturesRestClient getRestClient() {
    if (restClient == null)
      restClient = new KucoinFuturesClientBuilder().buildRestClient();
    return restClient;
  }

  /*private static ExchangeInfo exchangeInfoCache = null;
  public static ExchangeInfo getExchangeInfo() throws Exception {
    if (exchangeInfoCache == null ||
        exchangeInfoCache.getServerTime() + EXCHANGE_INFO_CACHE_LIVE_TIME < DateUtils.currentTimeMillis()) {
      exchangeInfoCache = UnsignedClient.getExchangeInformation();
      exchangeInfoCache.setServerTime(DateUtils.currentTimeMillis());

    }
    return exchangeInfoCache;
  }*/

  //TODO CACHE!!!
  public static ContractResponse getSymbolInfo(String symbol) throws Exception {
    return Utils.executeInFewAttempts(() ->
        getRestClient().symbolAPI().getContract(convertSymbolFormat(symbol)),
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public static ContractResponse getSymbolInfoSafe(String symbol) {
    try {
      return getSymbolInfo(symbol);
    } catch (Exception e) {
      LOGGER.error("Getting symbol info failed", e);
    }
    return null;
  }

  public static synchronized TickerResponse getLastPrice(String symbol) {
    try {
      return Utils.executeInFewAttempts(() ->
          getRestClient().tickerAPI().getTicker(convertSymbolFormat(symbol)),
          REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
    } catch (Exception e) {
      KucoinOrdersProcessor.LOGGER.warn("Error occurred while getting last price", e);
      return null;
    }
  }

  public static String getSymbolsList() throws Exception {
    StringBuilder result = new StringBuilder();
    List<ContractResponse> openContractList = Utils.executeInFewAttempts(() ->
        getRestClient().symbolAPI().getOpenContractList(), REQUEST_ATTEMPTS_COUNT,
        FAILED_REQUEST_REPEAT_PAUSE);
    for (ContractResponse resp : openContractList) {
      result.append(resp.toString()).append(System.lineSeparator());
    }
    return result.toString();
  }

  /*public static void main(String[] args) throws Exception {
    System.out.println(getSymbolsList());
  }*/

  public static String convertSymbolFormat(String symbol) {
    if (symbol.startsWith("BTC"))
      symbol = symbol.replaceAll("BTC", "XBT");
    return symbol + "M";
  }

}
