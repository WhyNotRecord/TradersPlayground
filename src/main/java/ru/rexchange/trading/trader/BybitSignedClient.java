package ru.rexchange.trading.trader;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
      return true;
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

  public Object getBalance(String currency) throws Exception {
    AccountDataRequest req = AccountDataRequest.builder().coins(currency).accountType(AccountType.UNIFIED).window("10000").build();
    synchronized(lock) {
      Map<String, Object> response = BybitFuturesApiProvider.checkResponse(aClient.getWalletBalance(req));
      return response;
    }
  }

  /*public List<Order> getFilledOrders(String symbol) throws Exception {
    synchronized(lock) {
      return client.getFilledOrders(symbol);
    }
  }*/

  public Object getOrders(boolean openOnly, String symbol) throws Exception {
    TradeOrderRequest request = TradeOrderRequest.builder().category(CategoryType.LINEAR).symbol(symbol).build();
    //todo openOnly
    synchronized(lock) {
      return client.getOpenOrders(request);
    }
  }

  /*public List<Order> getOpenOrders() throws Exception {
    synchronized(lock) {
      return client.getOpenOrders();
    }
  }*/

  public String setLeverage(String pair, Integer leverage) throws Exception {
    String leverageParam = String.valueOf(leverage);
    PositionDataRequest request = PositionDataRequest.builder().
        category(CategoryType.LINEAR).symbol(pair).buyLeverage(leverageParam).sellLeverage(leverageParam).build();
    synchronized(lock) {
      Object response = pClient.setPositionLeverage(request);
      System.out.println(response);
      return response.toString();//todo
    }
  }

  /*public Order cancelOrder(String symbol, Long orderId, String clientOrderId) throws Exception {
    synchronized(lock) {
      return client.cancelOrder(symbol, orderId, clientOrderId);
    }
  }*/

  public Map<String, Object> placeOrder(String symbol, Side side, PositionIdx mode, TradeOrderType orderType,
                           String quantity, String price) {
    try {
      var orderRequest = TradeOrderRequest.builder()
          .category(CategoryType.LINEAR)
          .symbol(symbol)
          .side(side)
          .orderType(orderType)
          .qty(quantity)
          .price(price)
          //.triggerPrice(price)
          //.triggerBy(TriggerBy.MARK_PRICE)
          .timeInForce(TimeInForce.GOOD_TILL_CANCEL)
          .positionIdx(mode)
          .isLeverage(1)
          .build();

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

  public Map<String, Object> queryOrder(String symbol, String orderId) {
    var orderRequest = TradeOrderRequest.builder()
        .category(CategoryType.LINEAR)
        .symbol(symbol).orderId(orderId).build();
    var response = client.getOpenOrders(orderRequest);
    Map<String, Object> result = BybitFuturesApiProvider.checkResponse(response);
    List<Map<String, Object>> orders = BybitFuturesApiProvider.extractList(result);
    return orders.isEmpty() ? null : orders.get(0);
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
