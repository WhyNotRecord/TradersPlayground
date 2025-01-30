package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;


@Entity
public class PositionInfo {
  public static final String TABLE_NAME = "POSITION_INFO";
  public static final String FIELD_POSITION_ID = "POSITION_ID";
  public static final String FIELD_TRADER_ID = "TRADER_ID";
  public static final String FIELD_TRADER_NAME = "TRADER_NAME";
  public static final String FIELD_OPEN_TIMESTAMP = "OPEN_TIMESTAMP";
  public static final String FIELD_STATUS = "STATUS";
  public static final String FIELD_CLOSE_TIMESTAMP = "CLOSE_TIMESTAMP";
  public static final String FIELD_DIRECTION = "DIRECTION";
  public static final String FIELD_SYMBOL = "SYMBOL";
  public static final String FIELD_AVERAGE_PRICE = "AVERAGE_PRICE";
  public static final String FIELD_AMOUNT = "AMOUNT";
  public static final String FIELD_ORDERS_COUNT = "ORDERS_COUNT";
  public static final String FIELD_COMMENT = "COMMENT";
  public static final String FIELD_PROFIT = "PROFIT";
  private static final String QUERY_LOAD_OBJECT = "SELECT POSITION_ID, TRADER_ID, TRADER_NAME, OPEN_TIMESTAMP, STATUS, CLOSE_TIMESTAMP, DIRECTION, SYMBOL, AVERAGE_PRICE, AMOUNT, ORDERS_COUNT, COMMENT, PROFIT FROM POSITION_INFO WHERE POSITION_ID = ? AND TRADER_ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO POSITION_INFO(POSITION_ID, TRADER_ID, TRADER_NAME, OPEN_TIMESTAMP, STATUS, CLOSE_TIMESTAMP, DIRECTION, SYMBOL, AVERAGE_PRICE, AMOUNT, ORDERS_COUNT, COMMENT, PROFIT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE POSITION_INFO SET TRADER_NAME = ?, OPEN_TIMESTAMP = ?, STATUS = ?, CLOSE_TIMESTAMP = ?, DIRECTION = ?, SYMBOL = ?, AVERAGE_PRICE = ?, AMOUNT = ?, ORDERS_COUNT = ?, COMMENT = ?, PROFIT = ? WHERE POSITION_ID = ? AND TRADER_ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String positionId;//primary key

  public String getPositionId() {
    return this.positionId;
  }

  public void setPositionId(String value) {
    this.positionId = value;
  }

  protected String traderId;//primary key

  public String getTraderId() {
    return this.traderId;
  }

  public void setTraderId(String value) {
    this.traderId = value;
  }

  protected String traderName;

  public String getTraderName() {
    return this.traderName;
  }

  public void setTraderName(String value) {
    this.traderName = value;
  }

  protected Timestamp openTimestamp;

  public Timestamp getOpenTimestamp() {
    return this.openTimestamp;
  }

  public void setOpenTimestamp(Timestamp value) {
    this.openTimestamp = value;
  }

  protected String status;

  public String getStatus() {
    return this.status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  protected Timestamp closeTimestamp;

  public Timestamp getCloseTimestamp() {
    return this.closeTimestamp;
  }

  public void setCloseTimestamp(Timestamp value) {
    this.closeTimestamp = value;
  }

  protected String direction;

  public String getDirection() {
    return this.direction;
  }

  public void setDirection(String value) {
    this.direction = value;
  }

  protected String symbol;

  public String getSymbol() {
    return this.symbol;
  }

  public void setSymbol(String value) {
    this.symbol = value;
  }

  protected Float averagePrice;

  public Float getAveragePrice() {
    return this.averagePrice;
  }

  public void setAveragePrice(Float value) {
    this.averagePrice = value;
  }

  protected Double amount;

  public Double getAmount() {
    return this.amount;
  }

  public void setAmount(Double value) {
    this.amount = value;
  }

  protected Integer ordersCount;

  public Integer getOrdersCount() {
    return this.ordersCount;
  }

  public void setOrdersCount(Integer value) {
    this.ordersCount = value;
  }

  protected String comment;

  public String getComment() {
    return this.comment;
  }

  public void setComment(String value) {
    this.comment = value;
  }

  protected Float profit;

  public Float getProfit() {
    return this.profit;
  }

  public void setProfit(Float value) {
    this.profit = value;
  }

  public PositionInfo() {
  }

  public PositionInfo(String positionId, String traderId) {
    this.positionId = positionId;
    this.traderId = traderId;
  }

  public static PositionInfo createAndLoad(Connection conn, String positionId, String traderId) throws SQLException, UserException, SystemException {
    PositionInfo instance = new PositionInfo();
    instance.positionId = positionId;
    instance.traderId = traderId;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (positionId == null)
      throw new SystemException("Primary key (positionId) is null");
    if (traderId == null)
      throw new SystemException("Primary key (traderId) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, positionId, traderId);
    if (result != null) {
      PositionInfo.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s, %s)", this.positionId, this.traderId);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, PositionInfo obj) {
    obj.positionId = (String) result.get(FIELD_POSITION_ID);
    obj.traderId = (String) result.get(FIELD_TRADER_ID);
    obj.traderName = (String) result.get(FIELD_TRADER_NAME);
    obj.openTimestamp = (Timestamp) result.get(FIELD_OPEN_TIMESTAMP);
    obj.status = (String) result.get(FIELD_STATUS);
    obj.closeTimestamp = (Timestamp) result.get(FIELD_CLOSE_TIMESTAMP);
    obj.direction = (String) result.get(FIELD_DIRECTION);
    obj.symbol = (String) result.get(FIELD_SYMBOL);
    obj.averagePrice = result.get(FIELD_AVERAGE_PRICE) == null ? null : ((Double) result.get(FIELD_AVERAGE_PRICE)).floatValue();
    obj.amount = (Double) result.get(FIELD_AMOUNT);
    obj.ordersCount = (Integer) result.get(FIELD_ORDERS_COUNT);
    obj.comment = (String) result.get(FIELD_COMMENT);
    obj.profit = result.get(FIELD_PROFIT) == null ? null : ((Double) result.get(FIELD_PROFIT)).floatValue();
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, positionId, traderId, traderName, openTimestamp, status, closeTimestamp, direction, symbol, averagePrice, amount, ordersCount, comment, profit);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, traderName, openTimestamp, status, closeTimestamp, direction, symbol, averagePrice, amount, ordersCount, comment, profit, positionId, traderId);
  }
}