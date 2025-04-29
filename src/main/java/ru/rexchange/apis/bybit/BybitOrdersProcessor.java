package ru.rexchange.apis.bybit;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.exception.UserException;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.PositionInfoObject;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.BybitSignedClient;
import ru.rexchange.trading.trader.futures.FuturesPositionContainer;

import java.math.BigDecimal;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class BybitOrdersProcessor extends AbstractOrdersProcessor<Map<String, Object>, BybitSignedClient> {
  protected static Logger LOGGER = LoggerFactory.getLogger(BybitOrdersProcessor.class);
  private static BybitOrdersProcessor instance = null;
  private static final long OPEN_ORDERS_CACHE_LIVE_TIME = 60 * 1000L;
  private boolean testOrders = false;

  protected BybitOrdersProcessor(boolean testOrders) {
    this.testOrders = testOrders;
  }

  public static BybitOrdersProcessor getInstance(boolean testOrders) {
    if (instance == null) {
      instance = new BybitOrdersProcessor(testOrders);
    }
    return instance;
  }

  public void setTestOrders(boolean value) {
    testOrders = value;
  }

  @Override
  public PositionContainer placeOrder(BybitSignedClient apiClient, AbstractPositionContainer<BybitSignedClient> openPosition,
                                      boolean limit, float price, String pair, double amount, Integer leverage,
                                      boolean buy, @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    return null;
  }

  @Override
  public PositionContainer placeMarketOrder(BybitSignedClient aClient, AbstractPositionContainer<BybitSignedClient> openPosition,
                                            String pair, double amount, Integer leverage, boolean buy) throws UserException {
    return null;
  }

  @Override
  public OrderInfoObject limitOrder(BybitSignedClient apiClient, float price, String pair, double amount, boolean buy, Integer leverage) throws UserException {
    return null;
  }

  @Override
  public PositionContainer createEmptyPositionContainer() {
    return null;
  }

  @Override
  public OrderInfoObject queryOrder(BybitSignedClient client, String symbol, String orderId) throws UserException {
    return null;
  }

  @Override
  public OrderInfoObject convertOrder(Map<String, Object> customOrder, String positionId) {
    return null;
  }

  @Override
  public boolean cancelOrder(BybitSignedClient apiClient, OrderInfoObject order) {
    return false;
  }

  @Override
  public float getLastPrice(String symbol) throws Exception {
    return 0;
  }

  @Override
  public Map<String, Object> updateOrder(BybitSignedClient apiAccess, OrderInfoObject order) throws SocketException, UnknownHostException {
    return Map.of();
  }

  public static void setLeverage(BybitSignedClient apiClient, String symbol, Integer leverage) throws Exception {
    LOGGER.debug("Setting leverage for pair {} to {}", symbol, leverage);
    apiClient.setLeverage(symbol, leverage);
  }


  public static class PositionContainer extends FuturesPositionContainer<BybitSignedClient> {
    public PositionContainer() {

    }

    public PositionContainer(PositionInfo position) {
      this.position = (PositionInfoObject) position;
    }

    @Override
    public int cancel(BybitSignedClient apiAccess) {
      return 0;
    }

    @Override
    protected BybitOrdersProcessor getOrdersProcessor() {
      return getInstance(false);
    }

    @Override
    public OrderInfoObject closeDeal(BybitSignedClient apiAccess) {
      return null;
    }

    @Override
    public OrderInfoObject closePartially(BybitSignedClient apiAccess, BigDecimal amount) {
      return null;//todo implement
    }

    @Override
    public boolean cancelSafetyOrders(BybitSignedClient apiAccess) {
      return false;
    }

    @Override
    public boolean rearrangeTakeProfit(BybitSignedClient apiAccess, BigDecimal newTp) {
      if (apiAccess == null) {
        LOGGER.warn("Can't rearrange take-profit: no valid client provided");
        return false;
      }
      return false;
    }

    @Override
    public Object rearrangeStopLoss(BybitSignedClient apiAccess, BigDecimal newSl) {
      return null;
    }

    @Override
    public List<OrderInfoObject> getSafetyOrders() {
      return null;
    }

    @Override
    public int incrementSafetyOrdersTriggered() {
      return 0;
    }

    @Override
    public boolean newSafetyOrdersFilled(boolean updateCounter) {
      return false;
    }

    @Override
    public boolean someOrdersFilled() {
      return false;
    }
  }

}
