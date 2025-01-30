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
import ru.rexchange.trading.trader.AbstractSignedClient;
import ru.rexchange.trading.trader.BybitSignedClient;
import ru.rexchange.trading.trader.futures.FuturesPositionContainer;

import java.math.BigDecimal;
import java.util.List;

public class BybitOrdersProcessor extends AbstractOrdersProcessor {
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
  public AbstractPositionContainer placeOrder(AbstractSignedClient apiClient, AbstractPositionContainer openPosition, boolean limit, float price, String pair, double amount, Integer leverage, boolean buy, @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    return null;
  }

  @Override
  public AbstractPositionContainer placeMarketOrder(AbstractSignedClient aClient, AbstractPositionContainer openPosition, String pair, double amount, Integer leverage, boolean buy) throws UserException {
    return null;
  }

  @Override
  public OrderInfoObject limitOrder(AbstractSignedClient apiClient, float price, String pair, double amount, boolean buy, Integer leverage) throws UserException {
    return null;
  }

  @Override
  public AbstractPositionContainer createEmptyPositionContainer() {
    return null;
  }

  @Override
  public OrderInfoObject queryOrder(AbstractSignedClient client, String symbol, String orderId) throws UserException {
    return null;
  }

  @Override
  public OrderInfoObject convertOrder(Object customOrder, String positionId) {
    return null;
  }

  @Override
  public boolean cancelOrder(AbstractSignedClient apiClient, OrderInfoObject order) {
    return false;
  }

  @Override
  public float getLastPrice(String symbol) throws Exception {
    return 0;
  }

  public static void setLeverage(BybitSignedClient apiClient, String symbol, Integer leverage) throws Exception {
    LOGGER.debug(String.format("Setting leverage for pair %s to %s", symbol, leverage));
    apiClient.setLeverage(symbol, leverage);
  }


  public static class PositionContainer extends FuturesPositionContainer {
    public PositionContainer() {

    }

    public PositionContainer(PositionInfo position) {
      this.position = (PositionInfoObject) position;
    }

    @Override
    public int update(AbstractSignedClient apiAccess) {
      return 0;
    }

    @Override
    public int cancel(AbstractSignedClient apiAccess) {
      return 0;
    }

    @Override
    protected AbstractOrdersProcessor getOrdersProcessor() {
      return getInstance(false);
    }

    @Override
    public OrderInfoObject closeDeal(AbstractSignedClient apiAccess) {
      return null;
    }

    @Override
    public boolean cancelSafetyOrders(AbstractSignedClient apiAccess) {
      return false;
    }

    @Override
    public Object rearrangeTakeProfit(AbstractSignedClient apiAccess, BigDecimal newTp) {
      return null;
    }

    @Override
    public Object rearrangeStopLoss(AbstractSignedClient apiAccess, BigDecimal newSl) {
      return null;
    }

    @Override
    public OrderInfoObject getBaseOrder() {
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
