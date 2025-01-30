package ru.rexchange.gen;

import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

//TODO перенести из пакета gen
public class OrderInfoObject extends OrderInfo {
  //public static final String STATUS_FINISHED = "FINISHED";
  private BigDecimal executedAmount = null;
  private BigDecimal avgPrice = null;
  private String positionSide = null;
  private String externalId = null;

  public static OrderInfoObject createAndLoad(String id, Connection conn)
      throws SQLException, UserException, SystemException {
    OrderInfoObject result = new OrderInfoObject();
    result.setOrderId(id);
    result.load(conn);
    /*if (result.getSymbol() == null)
      result.setSymbol(TradeUtils.getPair(result.getBaseIndex(), result.getQuotedIndex()));*/
    return result;
  }

  public static OrderInfoObject createNew(String id) {
    OrderInfoObject result = new OrderInfoObject();
    //result.setTraderId(trader);
    result.setOrderId(id);
    return result;
  }

  public boolean update(OrderInfoObject srcOrder) {
    this.setOrderTimestamp(srcOrder.getOrderTimestamp());
    this.setAmount(srcOrder.getAmount());
    this.setSymbol(srcOrder.getSymbol());
    this.setDirection(srcOrder.getDirection());
    this.setPrice(srcOrder.getPrice());
    this.setStatus(srcOrder.getStatus());
    this.setAvgPrice(srcOrder.getAvgPrice());
    this.setExecutedAmount(srcOrder.getExecutedAmount());
    this.setPositionSide(srcOrder.getPositionSide());
    return true;
  }

  public boolean statusIsNew() {
    return OrderStatus.NEW.equals(this.getStatus());
  }

  public BigDecimal getExecutedAmount() {
    return executedAmount == null ? BigDecimal.valueOf(getAmount()) : executedAmount;
  }

  public BigDecimal getAvgPrice() {
    return avgPrice == null ? BigDecimal.valueOf(getPrice()) : avgPrice;
  }

  public String getPositionSide() {
    return positionSide;
  }

  public void setExecutedAmount(BigDecimal value) {
    this.executedAmount = value;
  }

  public void setAvgPrice(BigDecimal avgPrice) {
    this.avgPrice = avgPrice;
  }

  public void setPositionSide(String positionSide) {
    this.positionSide = positionSide;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof OrderInfoObject))
      return false;
    OrderInfoObject that = (OrderInfoObject) o;
    return Objects.equals(getOrderId(), that.getOrderId()) &&
        Objects.equals(getSymbol(), that.getSymbol());
  }

  @Override
  public String toString() {
    return String.format("%s order (%s) on %s for %s by %s",
        getDirection(), getStatus(), getSymbol(), getAmount(), getPrice());
  }

  public interface Type {
    String BASE_ORDER = "BO";
    String STOP_LOSS = "SL";
    String TAKE_PROFIT = "TP";
    String CLOSE_ORDER = "CO";
    String SAFETY_ORDER = "SO";
  }

  public interface OrderStatus {
    String NEW = "NEW";
    String FILLED = "FILLED";
    String CANCELED = "CANCELED";
    String EXPIRED = "EXPIRED";
    //String FINISHED = STATUS_FINISHED;
  }
  /*
   * String orderId
   * String positionId
   * String direction
   * String symbol
   * String type (B/SL/TP/SO)
   * float price
   * String status
   * float profit
   */
}
