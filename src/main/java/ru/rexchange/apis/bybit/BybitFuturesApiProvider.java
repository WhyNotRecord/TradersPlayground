package ru.rexchange.apis.bybit;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import ru.rexchange.exception.SystemException;
import ru.rexchange.trading.trader.BybitSignedClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

//Здесь будут собраны методы для обращения к API биржи, не требующие авторизации (ключа API)
public class BybitFuturesApiProvider {
  private static BybitApiMarketRestClient client = null;
  public static boolean canTrade(BybitSignedClient client) throws Exception {
    return client.canTrade();
  }

  public static Float getLastPrice(String symbol) {
    MarketDataRequest request = MarketDataRequest.builder().category(CategoryType.LINEAR).symbol(symbol).build();
    Map<String, Object> response = checkResponse(getPublicMarketClient().getMarketTickers(request));
    List<Map<String, Object>> list = extractList(response);
    if (list.isEmpty())
      return null;
    return Float.parseFloat((String) list.get(0).get("lastPrice"));
  }

  /**
   * Метод для получения параметров торговли вроде tickSize, минимальный и максимальный размер ордера и т. д.
   * @param symbol - символ торговой пары
   * @return словарь с ключами: symbol, priceScale, priceFilter (словарь; внутри minPrice, maxPrice, tickSize),
   * lotSizeFilter (словарь; внутри minNotionalValue, maxOrderQty, maxMktOrderQty, minOrderQty, qtyStep),
   * leverageFilter(словарь; внутри minLeverage, maxLeverage, leverageStep), copyTrading

   * Пример:
   * {symbol=KASUSDT, contractType=LinearPerpetual, status=Trading, baseCoin=KAS, quoteCoin=USDT,
   * launchTime=1691138081000, deliveryTime=0, deliveryFeeRate=, priceScale=5,
   * leverageFilter={minLeverage=1, maxLeverage=50.00, leverageStep=0.01},
   * priceFilter={minPrice=0.00001, maxPrice=199.99998, tickSize=0.00001},
   * lotSizeFilter={maxOrderQty=4616000, minOrderQty=10, qtyStep=10, maxMktOrderQty=774000, minNotionalValue=5},
   * unifiedMarginTrade=true, fundingInterval=240, settleCoin=USDT, copyTrading=both,
   * upperFundingRate=0.01, lowerFundingRate=-0.01, riskParameters={priceLimitRatioX=0.15, priceLimitRatioY=0.3}}
   */
  public static Map<String, Object> getInstrumentInfo(String symbol) {
    MarketDataRequest request = MarketDataRequest.builder().category(CategoryType.LINEAR).symbol(symbol).build();
    Map<String, Object> response = checkResponse(getPublicMarketClient().getInstrumentsInfo(request));
    List<Map<String, Object>> list = extractList(response);
    return list.isEmpty() ? null : list.get(0);
  }

  private static BybitApiMarketRestClient getPublicMarketClient() {
    if (client == null) {
      BybitApiClientFactory factory =
          BybitApiClientFactory.newInstance(BybitApiConfig.MAINNET_DOMAIN);
      client = factory.newMarketDataRestClient();
    }
    return client;
  }

  public static Map<String, Object> checkResponse(Object response) {
    Map<String, Object> map = (Map<String, Object>) response;
    if (!Objects.equals(map.get("retCode"), 0) && map.containsKey("retMsg"))
      throw new SystemException(String.valueOf(map.get("retMsg")));
    Map<String, Object> result = (Map<String, Object>) map.get("result");
    System.out.println("Successful call, response:\n" + result);
    return result;
  }

  public static List<Map<String, Object>> extractList(Map<String, Object> result) {
    if (!result.containsKey("list"))
      return Collections.emptyList();
    return (List<Map<String, Object>>)(result.get("list"));
  }

  public static void main(String[] args) {
    String symbol = "KASUSDT";
    Map<String, Object> info = getInstrumentInfo(symbol);
    System.out.println(info);
    System.out.println(getLastPrice(symbol));
  }
}
