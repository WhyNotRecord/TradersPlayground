package ru.rexchange.trading;

import ru.rexchange.exception.UserException;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfoObject;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.AbstractSignedClient;

import javax.annotation.Nullable;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.UUID;

public abstract class AbstractOrdersProcessor<O, C extends AbstractSignedClient> {

  public AbstractPositionContainer<C> placeOrder(C apiClient, float price, String pair, double amount, Integer leverage,
                                              boolean buy, @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    return placeOrder(apiClient, null, true, price, pair, amount, leverage, buy, stopLoss, takeProfit);
  }

  /*public AbstractPositionContainer placeOrder(Object apiClient, float price, String pair, float amount,
                                              boolean buy) throws UserException {
    return placeOrder(apiClient, price, pair, amount, null, buy, null, null);
  }*/

  public abstract AbstractPositionContainer<C> placeOrder(C apiClient, AbstractPositionContainer<C> openPosition,
                                                       boolean limit, float price, String pair,
                                                       double amount, Integer leverage, boolean buy,
                                                       @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException;
  public abstract AbstractPositionContainer<C> placeMarketOrder(C aClient, AbstractPositionContainer<C> openPosition,
                                                             String pair, double amount, Integer leverage,
                                                             boolean buy) throws UserException;

  public abstract OrderInfoObject limitOrder(C apiClient, float price, String pair,
                                             double amount, boolean buy, Integer leverage) throws UserException;

  public AbstractPositionContainer<C> createPositionContainer(OrderInfoObject order) {
    AbstractPositionContainer<C> result = createEmptyPositionContainer();
    result.setPositionInfo(preparePositionInfo(order));
    result.addOrder(order);
    return result;
  }

  public abstract AbstractPositionContainer<C> createEmptyPositionContainer();

  //protected abstract AbstractPositionContainer createPositionContainer(Object customOrder);

  //protected abstract PositionInfo preparePositionInfo(Object customOrder);

  protected PositionInfoObject preparePositionInfo(OrderInfoObject order) {
    PositionInfoObject result = PositionInfoObject.createNew(null, generatePositionId());
    if (order.statusIsNew() || order.getExecutedAmount() == null || order.getAvgPrice() == null) {
      result.setAmount(order.getAmount());
      result.setAveragePrice(order.getPrice());
    } else {
      result.setAmount(order.getExecutedAmount().doubleValue());
      result.setAveragePrice(order.getAvgPrice().floatValue());
    }
    result.setOpenTimestamp(order.getOrderTimestamp());//todo разделить createTime и openTime
    result.setOrdersCount(1);
    result.setSymbol(order.getSymbol());
    //result.setComment(String.valueOf(bOrder.getParameter(AbstractPositionContainer.PARAM_COMMENT)));
    result.setStatus(PositionInfoObject.PositionStatus.NEW.name());
    result.setDirection(order.getDirection());
    return result;

  }

  protected static String generatePositionId() {
    return UUID.randomUUID().toString();
  }

  //public abstract Integer getLeverage(Object client, String symbol);

  public abstract OrderInfoObject queryOrder(C client, String symbol, String orderId) throws UserException;

  public abstract OrderInfoObject convertOrder(O customOrder, String positionId);

  protected int getLimitOrderShiftInTicks() {
    return 2;
  }

  public abstract boolean cancelOrder(C apiClient, OrderInfoObject order);

  public abstract float getLastPrice(String symbol) throws Exception;

  public abstract O updateOrder(C apiAccess, OrderInfoObject order) throws SocketException, UnknownHostException;
}
