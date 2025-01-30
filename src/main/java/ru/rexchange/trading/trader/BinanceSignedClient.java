package ru.rexchange.trading.trader;

import binance.futures.enums.*;
import binance.futures.impl.SignedClient;
import binance.futures.model.AccountBalance;
import binance.futures.model.Order;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.trading.TraderAuthenticator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinanceSignedClient extends AbstractSignedClient {
  private static final long POSITION_MODE_CACHE_LIVE_PERIOD = 30 * 1000L;
  private static final Object lock = new Object();
  private final String authId;
  private final SignedClient client;
  private static final Map<String, PositionMode> positionModesCache = new HashMap<>();
  private static final Map<String, Long> positionModeTimestamps = new HashMap<>();

  public BinanceSignedClient(TraderAuthenticator auth) {
    client = new SignedClient(auth.getPublicKey(), auth.getPrivateKey());
    this.authId = auth.toString();
  }

  public PositionMode getPositionMode() throws Exception {
    if (!positionModesCache.containsKey(authId) ||
        !positionModeTimestamps.containsKey(authId) ||
        positionModeTimestamps.get(authId) + POSITION_MODE_CACHE_LIVE_PERIOD < DateUtils.currentTimeMillis()) {
      synchronized(lock) {
        PositionMode mode = client.getPositionMode();
        positionModesCache.put(authId, mode);
        positionModeTimestamps.put(authId, DateUtils.currentTimeMillis());
      }
    }
    return positionModesCache.get(authId);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "#" + this.authId;
  }

  public boolean canTrade() throws Exception {
    synchronized(lock) {
      return client.canTrade();
    }
  }

  public List<AccountBalance> getBalance() throws Exception {
    synchronized(lock) {
      return client.getBalance();
    }
  }

  public List<Order> getFilledOrders(String symbol) throws Exception {
    synchronized(lock) {
      return client.getFilledOrders(symbol);
    }
  }

  public List<Order> getOrders(boolean openOnly, String symbol) throws Exception {
    synchronized(lock) {
      return client.getOrders(openOnly, symbol);
    }
  }

  //@Override
  public void setPositionMode(boolean hedge) throws Exception {
    synchronized(lock) {
      client.setPositionMode(hedge);
    }
  }

  @Override
  public boolean canWithdraw() throws Exception {
    synchronized(lock) {
      return client.getAccountData().canWithdraw();
    }
  }

  public Order postOrder(String pair, OrderSide orderSide, PositionSide positionSide, OrderType orderType,
                         TimeInForce timeInForce, String quantity, String price,
                         Boolean reduceOnly, String newClientOrderId, String stopPrice, WorkingType workingType,
                         NewOrderRespType newOrderRespType, Boolean closePosition) throws Exception {
    synchronized(lock) {
      return client.postOrder(pair, orderSide, positionSide, orderType, timeInForce, quantity, price,
          reduceOnly, newClientOrderId, stopPrice, workingType, newOrderRespType, closePosition);
    }
  }

  public List<Order> getOpenOrders() throws Exception {
    synchronized(lock) {
      return client.getOpenOrders();
    }
  }

  public String setLeverage(String pair, Integer leverage) throws Exception {
    synchronized(lock) {
      return client.setLeverage(pair, leverage);
    }
  }

  public Order cancelOrder(String symbol, Long orderId, String clientOrderId) throws Exception {
    synchronized(lock) {
      return client.cancelOrder(symbol, orderId, clientOrderId);
    }
  }

  public Order queryOrder(String symbol, Long orderId, String clientOrderId) throws Exception {
    synchronized(lock) {
      return client.queryOrder(symbol, orderId, clientOrderId);
    }
  }
}
