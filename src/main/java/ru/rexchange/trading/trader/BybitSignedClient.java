package ru.rexchange.trading.trader;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.TriggerBy;
import com.bybit.api.client.domain.account.AccountType;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.domain.position.PositionMode;
import com.bybit.api.client.domain.position.request.PositionDataRequest;
import com.bybit.api.client.domain.trade.PositionIdx;
import com.bybit.api.client.domain.trade.Side;
import com.bybit.api.client.domain.trade.TimeInForce;
import com.bybit.api.client.domain.trade.request.TradeOrderRequest;
import com.bybit.api.client.restApi.BybitApiAccountRestClient;
import com.bybit.api.client.restApi.BybitApiPositionRestClient;
import com.bybit.api.client.restApi.BybitApiTradeRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.apis.bybit.BybitFuturesApiProvider;
import ru.rexchange.exception.SystemException;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.futures.BybitOrder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//Здесь будут собраны методы для обращения к API биржи, требующие ключ API
public class BybitSignedClient extends AbstractSignedClient {
  protected static Logger LOGGER = LoggerFactory.getLogger(BybitSignedClient.class);
  public static final String FIELD_ORDER_ID = "orderId";
  private static final Object lock = new Object();
  private final String authId;
  private final BybitApiTradeRestClient client;
  private final BybitApiPositionRestClient pClient;
  private final BybitApiAccountRestClient aClient;
  //private PositionMode positionMode = null;
  private static final Map<String, PositionMode> positionModesCache = new ConcurrentHashMap<>();

  public BybitSignedClient(TraderAuthenticator auth) {
    BybitApiClientFactory factory =
        BybitApiClientFactory.newInstance(auth.getPublicKey(), auth.getPrivateKey(), BybitApiConfig.MAINNET_DOMAIN);
    client = factory.newTradeRestClient();
    pClient = factory.newPositionRestClient();
    aClient = factory.newAccountRestClient();
    this.authId = auth.toString();
  }

  public boolean canTrade() throws Exception {
    synchronized(lock) {
      return true;//todo
    }
  }

  public PositionMode getPositionMode(String symbol) throws Exception {
    synchronized (positionModesCache) {
      if (!positionModesCache.containsKey(symbol)) {
        return PositionMode.MERGED_SINGLE;
      }
    }
    return positionModesCache.get(symbol);
  }

  public void setPositionMode(String symbol, boolean hedge) throws Exception {
    PositionDataRequest request = PositionDataRequest.builder().category(CategoryType.LINEAR).
            positionMode(hedge ? PositionMode.BOTH_SIDES : PositionMode.MERGED_SINGLE).symbol(symbol).build();
    PositionMode positionMode = hedge ? PositionMode.BOTH_SIDES : PositionMode.MERGED_SINGLE;
    synchronized(lock) {
      try {
        Map<String, Object> result = BybitFuturesApiProvider.checkResponse(pClient.switchPositionMode(request));
      } catch (SystemException e) {
        if (!e.getMessage().contains("is not modified"))
          throw e;
      }
    }
    synchronized (positionModesCache) {
      positionModesCache.put(symbol, positionMode);
    }
  }


  @Override
  public boolean canWithdraw() throws Exception {
    synchronized(lock) {
      return false;
    }
  }

  public Map<String, Object> getBalance(String currency) throws Exception {
    AccountDataRequest req = AccountDataRequest.builder().coins(currency).accountType(AccountType.UNIFIED).window("10000").build();
    synchronized(lock) {
      Map<String, Object> response = BybitFuturesApiProvider.checkResponse(aClient.getWalletBalance(req));
      List<Map<String, Object>> lst = BybitFuturesApiProvider.extractList(response);
      return lst.isEmpty() ? Collections.emptyMap() : lst.get(0);
    }
  }

  public Float[] getBalancesFloat(String currency) {
    try {
      Map<String, Object> result = getBalance(currency);
      // totalAvailableBalance / totalWalletBalance
      float totalAvailableBalance = Float.parseFloat(result.get("totalAvailableBalance").toString());
      float totalWalletBalance = Float.parseFloat(result.get("totalWalletBalance").toString());
      return new Float[] {totalAvailableBalance, totalWalletBalance};
    } catch (Exception e) {
      LOGGER.warn("Error getting balances", e);
      return null;
    }
  }

  /*public List<Order> getFilledOrders(String symbol) throws Exception {
    synchronized(lock) {
      return client.getFilledOrders(symbol);
    }
  }*/

  /**
   * Возвращает список сведений ордеров
   * @param openOnly: 0 - только активные, 1 - только неактивные, 2 - все
   */ //TODO сделать обёртку для мапы сведений об ордере
  private List<BybitOrder> getOrders(int openOnly, String symbol) throws Exception {
    TradeOrderRequest request = TradeOrderRequest.builder().category(CategoryType.LINEAR).symbol(symbol).
        openOnly(openOnly).build();
    synchronized(lock) {
      Map<String, Object> response = BybitFuturesApiProvider.checkResponse(client.getOpenOrders(request));
      List<Map<String, Object>> rawOrders = BybitFuturesApiProvider.extractList(response);
      return rawOrders.stream().map(BybitOrder::new).collect(Collectors.toList());
    }
  }

  /**
   * Возвращает список сведений ордеров
   * @param openOnly: true - только активные, false - только неактивные (в т.ч. исполненные), null - все
   */
  public List<BybitOrder> getOrders(Boolean openOnly, String symbol) throws Exception {
    if (openOnly == null)
      return getOrders(2, symbol);
    return getOrders(openOnly ? 0 : 1, symbol);
  }

  /*public List<Order> getOpenOrders() throws Exception {
    synchronized(lock) {
      return client.getOpenOrders();
    }
  }*/

  public boolean setLeverage(String pair, Integer leverage) throws Exception {
    String leverageParam = String.valueOf(leverage);
    PositionDataRequest request = PositionDataRequest.builder().
        category(CategoryType.LINEAR).symbol(pair).buyLeverage(leverageParam).sellLeverage(leverageParam).build();
    synchronized(lock) {
      Object response = pClient.setPositionLeverage(request);
      Map<String, Object> result = BybitFuturesApiProvider.checkResponse(response);
      return true;
    }
  }

  /*public Order cancelOrder(String symbol, Long orderId, String clientOrderId) throws Exception {
    synchronized(lock) {
      return client.cancelOrder(symbol, orderId, clientOrderId);
    }
  }*/

  /**
   *
   * @param symbol
   * @param side
   * @param mode
   * @param orderType
   * @param quantity
   * @param price
   * @param reduceOnly
   * @param triggerDirection 1 - triggered when price rises to triggerPrice, 2 - triggered when price falls to triggerPrice
   * @return
   */
  public BybitOrder placeOrder(String symbol, Side side, PositionIdx mode, TradeOrderType orderType,
                               String quantity, String price, Boolean reduceOnly, Integer triggerDirection) {
    try {
      TradeOrderRequest.TradeOrderRequestBuilder builder = TradeOrderRequest.builder().category(CategoryType.LINEAR)
          .symbol(symbol).side(side).orderType(orderType).positionIdx(mode)
          .qty(quantity).price(price).timeInForce(TimeInForce.GOOD_TILL_CANCEL) .isLeverage(1);
      if (reduceOnly != null)
        builder.reduceOnly(reduceOnly);
      if (triggerDirection != null)
        builder.triggerDirection(triggerDirection).triggerPrice(price).triggerBy(TriggerBy.MARK_PRICE);
      TradeOrderRequest orderRequest = builder.build();
      var response = client.createOrder(orderRequest);
      Map<String, Object> result = BybitFuturesApiProvider.checkResponse(response);
      //LOGGER.info("Order placed: {}", response);
      if (!result.containsKey(FIELD_ORDER_ID))
        throw new SystemException("Response doesn't contain orderId");
      return queryOrder(symbol, (String) result.get(FIELD_ORDER_ID));
    } catch (Exception e) {
      LOGGER.error("Error placing order: {}", e.getMessage());
      throw new RuntimeException("Failed to place order", e);
    }
  }

  /**
   * Возвращает сведения ордера с заданным orderId
   * @param symbol
   * @param orderId
   * @return словарь со сведениями

   * Пример:
   * {symbol=ETHUSDT, orderType=Limit, orderLinkId=, slLimitPrice=0, orderId=085b2786-c7b1-4432-a317-93ca308b7780,
   * cancelType=UNKNOWN, avgPrice=, stopOrderType=, lastPriceOnCreated=1796.51, orderStatus=New,
   * createType=CreateByUser, takeProfit=, cumExecValue=0, tpslMode=, smpType=None, triggerDirection=0,
   * blockTradeId=, isLeverage=, rejectReason=EC_NoError, price=1500, orderIv=, createdTime=1746003818818,
   * tpTriggerBy=, positionIdx=1, timeInForce=GTC, leavesValue=15, updatedTime=1746003818821, side=Buy,
   * smpGroup=0, triggerPrice=, tpLimitPrice=0, cumExecFee=0, leavesQty=0.01, slTriggerBy=, closeOnTrigger=false,
   * placeType=, cumExecQty=0, reduceOnly=false, qty=0.01, stopLoss=, marketUnit=, smpOrderId=, triggerBy=}
   */
  public BybitOrder queryOrder(String symbol, String orderId) {
    var orderRequest = TradeOrderRequest.builder()
        .category(CategoryType.LINEAR)
        .symbol(symbol).orderId(orderId).build();
    var response = client.getOpenOrders(orderRequest);
    Map<String, Object> result = BybitFuturesApiProvider.checkResponse(response);
    List<Map<String, Object>> orders = BybitFuturesApiProvider.extractList(result);
    return orders.isEmpty() ? null : new BybitOrder(orders.get(0));
  }

  public boolean cancelOrder(String symbol, String orderId) {
    try {
      var cancelRequest = TradeOrderRequest.builder()
          .category(CategoryType.LINEAR)
          .symbol(symbol)
          .orderId(orderId)
          .build();

      Object response = client.cancelOrder(cancelRequest);
      Map<String, Object> result = BybitFuturesApiProvider.checkResponse(response);
      //LOGGER.info("Order cancelled: {}", response);
      return result.containsKey(FIELD_ORDER_ID) && orderId.equals(result.get(FIELD_ORDER_ID));
    } catch (Exception e) {
      LOGGER.error("Error cancelling order: {}", e.getMessage());
      throw new RuntimeException("Failed to cancel order", e);
    }
  }

  /*private Side mapOrderSide(String side) {
        return switch (side) {
            case BUY -> Side.BUY;
            case SELL -> Side.SELL;
        };
    }

    private TradeOrderType mapOrderType(String type) {
        return switch (type) {
            case MARKET -> TradeOrderType.MARKET;
            case LIMIT -> TradeOrderType.LIMIT;
        };
    }*/

  @Override
  public String toString() {
    return getClass().getSimpleName() + "#" + this.authId;
  }
}
