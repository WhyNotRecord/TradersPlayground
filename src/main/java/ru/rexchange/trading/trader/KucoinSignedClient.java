package ru.rexchange.trading.trader;

import com.kucoin.futures.core.KucoinFuturesClientBuilder;
import com.kucoin.futures.core.KucoinFuturesRestClient;
import com.kucoin.futures.core.rest.request.ChangeCrossUserLeverageRequest;
import com.kucoin.futures.core.rest.request.ChangeMarginRequest;
import com.kucoin.futures.core.rest.request.OrderCreateApiRequest;
import com.kucoin.futures.core.rest.response.AccountOverviewResponse;
import com.kucoin.futures.core.rest.response.ContractResponse;
import com.kucoin.futures.core.rest.response.OrderCreateResponse;
import com.kucoin.futures.core.rest.response.OrderResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.rexchange.apis.kucoin.KucoinFuturesApiProvider;
import ru.rexchange.apis.kucoin.KucoinOrdersProcessor;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.tools.StringUtils;
import ru.rexchange.tools.Utils;
import ru.rexchange.trading.TraderAuthenticator;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class KucoinSignedClient extends AbstractSignedClient {
  public static final long BALANCE_INFO_CACHE_LIVE_TIME = 15 * 1000L;
  private static final Logger LOGGER = LoggerFactory.getLogger(KucoinSignedClient.class);
  //private static final long POSITION_MODE_CACHE_LIVE_PERIOD = 30 * 1000L;
  public static final int REQUEST_ATTEMPTS_COUNT = 3;
  public static final long FAILED_REQUEST_REPEAT_PAUSE = 500L;
  public static final String CROSS_MARGIN_MODE = "CROSS";
  private static final Map<String, BalanceInfo> balanceInfoCache = new HashMap<>();
  private final String authId;
  private KucoinFuturesRestClient kucoinFuturesRestApiClient = null;

  public KucoinSignedClient(TraderAuthenticator auth) {
    KucoinFuturesClientBuilder builder = new KucoinFuturesClientBuilder().
        withApiKey(auth.getPublicKey(), auth.getPrivateKey(), auth.getPersonalKey());
    kucoinFuturesRestApiClient = builder.buildRestClient();
    this.authId = auth.toString();
  }

  private static synchronized AccountOverviewResponse getCachedBalance(String currency, KucoinSignedClient client) throws Exception {
    String key = client.toString() + currency;
    if (!balanceInfoCache.containsKey(key) ||
        balanceInfoCache.get(key).getTimestamp() + BALANCE_INFO_CACHE_LIVE_TIME < DateUtils.currentTimeMillis()) {
      balanceInfoCache.put(key, new BalanceInfo(client.getBalance(currency)));
    }
    return balanceInfoCache.get(key).getBalance();
  }

  public static BigDecimal getFreeAssetBalance(String currency, KucoinSignedClient client) throws Exception {
    AccountOverviewResponse response = getCachedBalance(currency, client);
    return response.getAvailableBalance();
  }

  public static BigDecimal getTotalAssetBalance(String currency, KucoinSignedClient client) throws Exception {
    AccountOverviewResponse response = getCachedBalance(currency, client);
    return response.getMarginBalance();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "#" + this.authId;
  }

  public boolean canTrade() {
    return true;
  }

  public OrderResponse postOrder(String symbol, String orderSide, String execType, String stop,
                                 String timeInForce, BigDecimal quantity, BigDecimal price, Integer leverage) throws Exception {
    return postOrder(symbol, orderSide, execType, stop, false, timeInForce, quantity, price, leverage);
  }

  public OrderResponse postOrder(String symbol, String orderSide, String execType, String stop, boolean close,
                                 String timeInForce, BigDecimal quantity, BigDecimal price, Integer leverage) throws Exception {
    //symbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
    OrderCreateApiRequest.OrderCreateApiRequestBuilder builder =
        OrderCreateApiRequest.builder().side(orderSide).symbol(symbol).type(execType).
        clientOid(UUID.randomUUID().toString()).marginMode(CROSS_MARGIN_MODE);
    if (timeInForce != null)
      builder = builder.timeInForce(timeInForce);
    if (stop != null) {
      builder = builder.stopPriceType("MP").stop(stop).closeOrder(true).stopPrice(price);
    } else {
      builder = builder.price(price).size(quantity);
    }
    if (close) {
      builder.closeOrder(true);
    }
    builder = builder.leverage(leverage != null ? String.valueOf(leverage) : "1");

    OrderCreateApiRequest createRequest = builder.build();

    OrderCreateResponse response = Utils.executeInFewAttempts(() ->
        kucoinFuturesRestApiClient.orderAPI().createOrder(createRequest),
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
    Utils.waitSafe(1500, LOGGER, "postOrder");//TODO не всегда успевает обработать за секунду, можем получить error.getOrder.orderNotExist
    return queryOrder(symbol, response.getOrderId(), null);
  }

  public AccountOverviewResponse getBalance(String currency) throws Exception {
    return Utils.executeInFewAttempts(() ->
        kucoinFuturesRestApiClient.accountAPI().accountOverview(currency),
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public static void main(String[] args) throws Exception {
    TraderAuthenticator auth = getKucanFakeAuthenticator();
    KucoinSignedClient client = new KucoinSignedClient(auth);
    Object result = client.getBalance("USDT");
    System.out.println(result);
    String rawSymbol = "ETHUSDT";
    String symbol = KucoinFuturesApiProvider.convertSymbolFormat(rawSymbol);
    System.out.println(StringUtils.toStringCommon(client.getOrders(true, rawSymbol)));
    System.out.println(StringUtils.toStringCommon(client.getFilledOrders(symbol)));
    ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfo(rawSymbol);
    BigDecimal preparedAmount = KucoinOrdersProcessor.getInstance(true).
        checkAndFitAmount(symbolInfo, 1.0f, 1.1f);
    System.out.println(preparedAmount);
    BigDecimal preparedPrice = KucoinOrdersProcessor.getInstance(true).
        checkAndFitPrice(symbolInfo, 1.2f, REQUEST_ATTEMPTS_COUNT);
    System.out.println(preparedPrice);
  }

  @NotNull
  private static TraderAuthenticator getKucanFakeAuthenticator() {
    return new TraderAuthenticator("Kucan", "1", "2", "3");
  }

  public OrderResponse queryOrder(String symbol, String orderId, String orderClientId) throws Exception {
    return Utils.executeInFewAttempts(() -> {
      if (orderId != null)
        return kucoinFuturesRestApiClient.orderAPI().getOrderDetail(orderId);
      else
        return kucoinFuturesRestApiClient.orderAPI().getOrderDetailByClientOid(orderClientId);
    }, REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public boolean cancelOrder(String symbol, String orderId, String orderClientOid) throws Exception {
    return Utils.executeInFewAttempts(() -> {
      if (orderId != null)
        return !kucoinFuturesRestApiClient.orderAPI().cancelOrder(orderId).getCancelledOrderIds().isEmpty();
      final String kSymbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
      return kucoinFuturesRestApiClient.orderAPI().cancelOrderByClientOid(orderClientOid, kSymbol).getClientOid() != null;
    }, REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public List<OrderResponse> getFilledOrders(String symbol) throws Exception {
    final String kSymbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
    return Utils.executeInFewAttempts(() ->
        kucoinFuturesRestApiClient.orderAPI().getRecentDoneOrders().stream().filter(
        orderResponse -> kSymbol.equals(orderResponse.getSymbol())).collect(Collectors.toList()),
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  /**
   * Возвращает список последних ордеров
   * @param active - выбирать только активные (открытые), неисполненные ордера
   * @param symbol - торговая пара
   * @return список ордеров
   */
  public List<OrderResponse> getOrders(boolean active, String symbol) throws Exception {
    String kSymbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
    return Utils.executeInFewAttempts(() -> {
          if (active)
            return kucoinFuturesRestApiClient.orderAPI().
                getUntriggeredStopOrderList(kSymbol, null, null, null).getItems();
          else //symbol, side, type, status
            return kucoinFuturesRestApiClient.orderAPI().
                getOrderList(kSymbol, null, null, OrderStatus.DONE, null).getItems();
        },
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public void switchMarginMode(String symbol) throws IOException {//todo продумать, в каких случаях вызывать
    String kSymbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
    if (!CROSS_MARGIN_MODE.equals(kucoinFuturesRestApiClient.positionAPI().getMarginMode(kSymbol).getMarginMode())) {
      kucoinFuturesRestApiClient.positionAPI().changeMarginMode(
          ChangeMarginRequest.builder().symbol(kSymbol).marginMode(CROSS_MARGIN_MODE).build());
    }
  }

  public boolean setLeverage(String symbol, Integer leverage) throws Exception {
    String kSymbol = KucoinFuturesApiProvider.convertSymbolFormat(symbol);
    ChangeCrossUserLeverageRequest request = ChangeCrossUserLeverageRequest.builder().
        symbol(kSymbol).leverage(String.valueOf(leverage)).build();
    return Utils.executeInFewAttempts(() ->
            kucoinFuturesRestApiClient.positionAPI().changeCrossUserLeverage(request),
        REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
  }

  public void setPositionMode(boolean hedge) throws Exception {
    //doesn't support hedge mode
  }

  @Override
  public boolean canWithdraw() throws Exception {
    return false;//seems like we can not find out
  }

  public interface OrderSide {
    String BUY = "buy";
    String SELL = "sell";
  }

  public interface OrderType {
    String LIMIT = "limit";
    String MARKET = "market";
  }

  /**
   * Возможные статусы ордера
   * Отменённые ордера также имеют статус done, но у них признак cancelExist=true
   */
  public interface OrderStatus {
    //String ACTIVE = "active";
    String OPEN = "open";
    String DONE = "done";
  }

  private static class BalanceInfo {
    private final AccountOverviewResponse balance;
    private final long timestamp;
    public BalanceInfo(AccountOverviewResponse balance) {
      this.balance = balance;
      this.timestamp = DateUtils.currentTimeMillis();
    }

    public AccountOverviewResponse getBalance() {
      return balance;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
