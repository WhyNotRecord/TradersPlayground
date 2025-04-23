package ru.rexchange.trading.trader;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.Consts;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfoObject;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.trading.AbstractOrdersProcessor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

public abstract class AbstractPositionContainer {
  protected static Logger LOGGER = LoggerFactory.getLogger(AbstractPositionContainer.class);
  protected static final List<String> NOT_ACTUAL_ORDER_STATUSES = Arrays.asList(
      OrderInfoObject.OrderStatus.CANCELED, OrderInfoObject.OrderStatus.EXPIRED);
  //private static final String PARAM_TAKE_PROFIT_PERCENT = "TP_PERCENT";
  //public static final String PARAM_STRATEGY_NAME = "STRATEGY_NAME";
  //public static final String PARAM_COMMENT = "COMMENT";
  @Getter
  protected final List<OrderInfoObject> orders = new ArrayList<>();
  protected final List<OrderInfoObject> outdatedOrders = new ArrayList<>();
  protected Map<String, Object> parameters = new HashMap<>();
  protected PositionInfoObject position;
  @Setter
  @Getter
  protected OrderInfoObject stopLossOrder;
  @Setter
  @Getter
  protected OrderInfoObject takeProfitOrder;

  /*@Deprecated
  public void addSafetyOrder(OrderInfoObject order) {
    addOrder(order);
  }*/

  public void addOrder(OrderInfoObject order) {
    if (!orders.contains(order)) {
      order.setPositionId(getPositionInfo().getPositionId());
      orders.add(order);
      updatePositionInfo();
      updatePositionStatus();
    }
  }

  public abstract int update(AbstractSignedClient apiAccess) throws UnknownHostException, SocketException;

  public abstract int cancel(AbstractSignedClient apiAccess);

  protected abstract AbstractOrdersProcessor getOrdersProcessor();

  public abstract OrderInfoObject closeDeal(AbstractSignedClient apiAccess);
  public abstract OrderInfoObject closePartially(AbstractSignedClient apiAccess, BigDecimal amount);

  protected void updatePositionStatus() {
    if (PositionInfoObject.PositionStatus.NEW.name().equals(getPositionInfo().getStatus())) {
      for (OrderInfoObject order : orders) {
        if (!order.statusIsNew())
          getPositionInfo().setStatus(PositionInfoObject.PositionStatus.OPEN.name());
      }
    }
    getAvgPrice(true);
  }

  public abstract boolean cancelSafetyOrders(AbstractSignedClient apiAccess);
  public abstract boolean rearrangeTakeProfit(AbstractSignedClient apiAccess, BigDecimal newTp);
  public abstract Object rearrangeStopLoss(AbstractSignedClient apiAccess, BigDecimal newSl);
  @Deprecated
  public abstract OrderInfoObject getBaseOrder();

  public OrderInfoObject getFirstOrder() {
    if (!getOrders().isEmpty())
      return getOrders().get(0);
    return null;
  }

  public OrderInfoObject getLastOrder() {
    if (!getOrders().isEmpty())
      return getOrders().get(getOrders().size() - 1);
    return null;
  }

  public OrderInfoObject getMaxOrder() {
    if (!getOrders().isEmpty())
      return getOrders().stream().max(Comparator.comparing(OrderInfoObject::getAmount)).orElse(null);
    return null;
  }

  public PositionInfoObject getPositionInfo() {
    return position;
  }

  public void setPositionInfo(PositionInfoObject position) {
    this.position = position;
    for (OrderInfoObject o : getOrders()) {
      if (o.getPositionId() == null)
        o.setPositionId(position.getPositionId());
    }
  }

  public BigDecimal getExecutedAmount() {
    BigDecimal result = BigDecimal.ZERO;//BigDecimal.valueOf(getPositionInfo().getAmount());
    for (OrderInfoObject o : orders) {
      BigDecimal coDirectional = BigDecimal.ONE;
      if (!o.getDirection().equals(getSide()))
        coDirectional = coDirectional.negate();
      if (OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()))
        result = result.add(o.getExecutedAmount().multiply(coDirectional));
    }
    return result;
  }

  public BigDecimal getAvgPrice() {
    return getAvgPrice(false);
  }

  public BigDecimal getAvgPrice(boolean recalculate) {//todo переделать метод нормально
    //Пока предполагаем, что метод вызывается только при выполненном базовом ордере (но это не всегда так)
    LOGGER.trace("{}::avgPrice requested, stored value - {}", this.hashCode(), getPositionInfo().getAveragePrice());
    if (getPositionInfo().getAveragePrice() == null || recalculate) {
      LOGGER.trace("{}::avgPrice calculation", this.hashCode());
      try {
        BigDecimal posValue = BigDecimal.ZERO;
        BigDecimal posAmount = BigDecimal.ZERO;
        for (OrderInfoObject o : orders) {
          if (OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()) && o.getExecutedAmount() != null && o.getAvgPrice() != null) {
            BigDecimal orderWeight = o.getExecutedAmount().multiply(o.getAvgPrice(), MathContext.DECIMAL32);
            BigDecimal coDirectional = BigDecimal.ONE;
            if (!o.getDirection().equals(getSide()))
              coDirectional = coDirectional.negate();
            posValue = posValue.add(orderWeight.multiply(coDirectional));
            posAmount = posAmount.add(o.getExecutedAmount().multiply(coDirectional));
          }
        }
        if (posAmount.equals(BigDecimal.ZERO)) {
          LOGGER.info("{}. Orders are not filled yet",
              getPositionInfo() == null ? "Unknown" : getPositionInfo().getPositionId());
          return orders.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(getBaseOrder().getPrice());
        }

        BigDecimal result = posValue.divide(posAmount, MathContext.DECIMAL32);
        getPositionInfo().setAveragePrice(result.floatValue());
        LOGGER.trace("{}::avgPrice calculated - {}", this.hashCode(), result.setScale(5, RoundingMode.CEILING));
        return result;
      } catch (Exception e) {
        LOGGER.trace("{}::avgPrice error", this.hashCode());
        LOGGER.error("Unexpected error", e);
        return BigDecimal.ZERO;
      }
    }
    return BigDecimal.valueOf(getPositionInfo().getAveragePrice());
  }

  public String getSide() {
    return getPositionInfo() == null ? getBaseOrder().getDirection() : getPositionInfo().getDirection();
  }

  public Object setParameter(String name, Object value) {
    return parameters.put(name, value);
  }

  public boolean hasParameter(String name) {
    return parameters.containsKey(name);
  }

  public Object getParameter(String name) {
    if (parameters.containsKey(name))
      return parameters.get(name);
    return null;
  }

  public boolean stopOrdersNotActual() {
    boolean slNotActual = stopLossOrder == null || NOT_ACTUAL_ORDER_STATUSES.contains(stopLossOrder.getStatus());
    boolean tpNotActual = takeProfitOrder == null || NOT_ACTUAL_ORDER_STATUSES.contains(takeProfitOrder.getStatus());
    return slNotActual && tpNotActual;
  }

  protected void updatePositionInfo() {
    double posAmount = 0.f, posValue = 0.f;
    for (OrderInfoObject o : getOrders()) {
      if (!OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()))
        continue;
      int coDirectional = o.getDirection().equals(getSide()) ? 1 : -1;
      posAmount += (o.getAmount() * coDirectional);
      posValue += (o.getAmount() * o.getPrice() * coDirectional);
    }
    if (getPositionInfo() != null) {
      getPositionInfo().setAveragePrice((float) (posValue / posAmount));
      getPositionInfo().setAmount(posAmount);
      getPositionInfo().setOrdersCount(getOrders().size());
    } else {
      LOGGER.info("Can't update position info. Requisites: QTY - {}, AVG - {}, CNT - {}",
          posAmount, posValue / posAmount, getOrders().size());
    }
  }

  //todo может брать getOrders и отбирать несработавшие
  public abstract List<OrderInfoObject> getSafetyOrders();

  public int getTriggeredOrdersCount() {
    //todo Проверить, что учитываются только сработавшие
    return (int) getSafetyOrders().stream().filter(orderInfoObject -> !orderInfoObject.statusIsNew()).count();
  }

  public abstract int incrementSafetyOrdersTriggered();

  public abstract boolean newSafetyOrdersFilled(boolean updateCounter);

  public abstract boolean someOrdersFilled();

  public List<OrderInfoObject> getOutdatedOrders() {
    return new ArrayList<>(outdatedOrders);
  }

  public boolean clearOutdatedOrders(boolean notActualOnly) {
    if (notActualOnly) {
      for (OrderInfoObject order : new LinkedList<>(outdatedOrders)) {
        if (!OrderInfoObject.OrderStatus.NEW.equals(order.getStatus()))
          outdatedOrders.remove(order);
      }
    } else
      outdatedOrders.clear();
    if (!outdatedOrders.isEmpty())
      LOGGER.debug("{} outdated orders left for {}", outdatedOrders.size(), getPositionInfo().getSymbol());
    return outdatedOrders.isEmpty();
  }

  public String toString(Float lastRate) {
    StringBuilder sb = new StringBuilder(256);
    Float positionProfit = null;
    PositionInfoObject pos = getPositionInfo();

    if (lastRate == null) {
      try {
        lastRate = getOrdersProcessor().getLastPrice(pos.getSymbol());
      } catch (Exception e) {
        LOGGER.warn("Unsuccessful price request", e);
      }
    }
    BigDecimal avgPrice = getAvgPrice();
    if (lastRate != null) {
      positionProfit = Consts.BUY.equals(getSide()) ?
          lastRate / avgPrice.floatValue() - 1.f :
          avgPrice.floatValue() / lastRate - 1.f;
    }
    sb.append(String.format("%s position on %s. AVG - %.7f (%s), QTY - %.3f",
        pos.getDirection(), pos.getSymbol(), avgPrice, pos.getStatus(), pos.getAmount()));

    if (stopLossOrder != null)
      sb.append(", SL - ").append(formatPrice(stopLossOrder.getPrice())).
          append(" (").append(stopLossOrder.getStatus()).append(")");
    if (takeProfitOrder != null)
      sb.append(", TP - ").append(formatPrice(takeProfitOrder.getPrice())).
          append(" (").append(takeProfitOrder.getStatus()).append(")");
    if (positionProfit != null)
      sb.append(", change: ").append(ru.rexchange.tools.StringUtils.printAsPercent(positionProfit));
    if (!orders.isEmpty())
      sb.append(". Has ").append(orders.size()).append(" orders");
    for (OrderInfoObject order : orders) {
      /*sb.append(System.lineSeparator());
      sb.append(String.format("%s order on %s from %s at %.7f is %s (QTY: %s)",
          so.getDirection(), so.getSymbol(), DateUtils.formatTimeMin(so.getOrderTimestamp()),
          so.getPrice(), so.getStatus(), so.getExecutedAmount()));*/
      //todo разобраться с отображением QTY (округлить)
      sb.append(System.lineSeparator())
          .append(order.getDirection()).append(" order on ").append(order.getSymbol())
          .append(" from ").append(DateUtils.formatTimeMin(order.getOrderTimestamp()))
          .append(" at ").append(formatPrice(order.getPrice()))
          .append(" is ").append(order.getStatus())
          .append(" (QTY: ").append(order.getExecutedAmount()).append(")");

    }
    return sb.toString();
  }

  @Nullable
  private String formatPrice(Float price) {
    return StringUtils.left(String.valueOf(price), 8);
  }

  public float getExpectedProfit(Float atPrice) {
    double profitLoss = 0f;

    PositionInfoObject pos = getPositionInfo();
    if (pos.getDirection().equalsIgnoreCase(Consts.BUY)) {
      profitLoss = (atPrice - pos.getAveragePrice()) * pos.getAmount();
    } else if (pos.getDirection().equalsIgnoreCase(Consts.SELL)) {
      profitLoss = (pos.getAveragePrice() - atPrice) * pos.getAmount();
    } else {
      LOGGER.warn("Invalid trade direction. Could be either 'buy' or 'sell'.");
    }

    return (float) profitLoss;
  }

  public float getExpectedValue(OrderInfoObject order) {
    return (float) (order.getPrice() * order.getAmount());
  }

  public Float getPositionWeight() {
    return getAvgPrice().floatValue() * getExecutedAmount().floatValue();
  }

  public BigDecimal getLastOrdersWeight(int count) {
    BigDecimal result = BigDecimal.ZERO;//todo check это безобразие
    List<OrderInfoObject> sorted = new ArrayList<>(orders);
    sorted.sort(Comparator.comparing(OrderInfoObject::getExecutedAmount));
    int counter = 0;
    for (int i = sorted.size() - 1; i > 0; i--) {
      OrderInfoObject o = sorted.get(i);
      BigDecimal coDirectional = BigDecimal.ONE;
      if (!o.getDirection().equals(getSide()))
        coDirectional = coDirectional.negate();
      // складываем, если side совпадает, иначе вычитаем
      if (OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()))
        result = result.add(o.getExecutedAmount().multiply(coDirectional));
      counter += coDirectional.intValue();
      if (counter >= count)
        break;
    }
    return result;
  }
}
