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
public class OrderInfo {
  public static final String TABLE_NAME = "ORDER_INFO";
  public static final String FIELD_ORDER_ID = "ORDER_ID";
  public static final String FIELD_POSITION_ID = "POSITION_ID";
  public static final String FIELD_ORDER_TIMESTAMP = "ORDER_TIMESTAMP";
  public static final String FIELD_STATUS = "STATUS";
  public static final String FIELD_DIRECTION = "DIRECTION";
  public static final String FIELD_TYPE = "TYPE";
  public static final String FIELD_SYMBOL = "SYMBOL";
  public static final String FIELD_PRICE = "PRICE";
  public static final String FIELD_AMOUNT = "AMOUNT";
  public static final String FIELD_COMMENT = "COMMENT";
  private static final String QUERY_LOAD_OBJECT = "SELECT ORDER_ID, POSITION_ID, ORDER_TIMESTAMP, STATUS, DIRECTION, TYPE, SYMBOL, PRICE, AMOUNT, COMMENT FROM ORDER_INFO WHERE ORDER_ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO ORDER_INFO(ORDER_ID, POSITION_ID, ORDER_TIMESTAMP, STATUS, DIRECTION, TYPE, SYMBOL, PRICE, AMOUNT, COMMENT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE ORDER_INFO SET POSITION_ID = ?, ORDER_TIMESTAMP = ?, STATUS = ?, DIRECTION = ?, TYPE = ?, SYMBOL = ?, PRICE = ?, AMOUNT = ?, COMMENT = ? WHERE ORDER_ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String orderId;//primary key

  public String getOrderId() {
    return this.orderId;
  }

  public void setOrderId(String value) {
    this.orderId = value;
  }

  protected String positionId;

  public String getPositionId() {
    return this.positionId;
  }

  public void setPositionId(String value) {
    this.positionId = value;
  }

  protected Timestamp orderTimestamp;

  public Timestamp getOrderTimestamp() {
    return this.orderTimestamp;
  }

  public void setOrderTimestamp(Timestamp value) {
    this.orderTimestamp = value;
  }

  protected String status;

  public String getStatus() {
    return this.status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  protected String direction;

  public String getDirection() {
    return this.direction;
  }

  public void setDirection(String value) {
    this.direction = value;
  }

  protected String type;

  public String getType() {
    return this.type;
  }

  public void setType(String value) {
    this.type = value;
  }

  protected String symbol;

  public String getSymbol() {
    return this.symbol;
  }

  public void setSymbol(String value) {
    this.symbol = value;
  }

  protected Float price;

  public Float getPrice() {
    return this.price;
  }

  public void setPrice(Float value) {
    this.price = value;
  }

  protected Double amount;

  public Double getAmount() {
    return this.amount;
  }

  public void setAmount(Double value) {
    this.amount = value;
  }

  protected String comment;

  public String getComment() {
    return this.comment;
  }

  public void setComment(String value) {
    this.comment = value;
  }

  public OrderInfo() {
  }

  public OrderInfo(String orderId) {
    this.orderId = orderId;
  }

  public static OrderInfo createAndLoad(Connection conn, String orderId) throws SQLException, UserException, SystemException {
    OrderInfo instance = new OrderInfo();
    instance.orderId = orderId;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (orderId == null)
      throw new SystemException("Primary key (orderId) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, orderId);
    if (result != null) {
      OrderInfo.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.orderId);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, OrderInfo obj) {
    obj.orderId = (String) result.get(FIELD_ORDER_ID);
    obj.positionId = (String) result.get(FIELD_POSITION_ID);
    obj.orderTimestamp = (Timestamp) result.get(FIELD_ORDER_TIMESTAMP);
    obj.status = (String) result.get(FIELD_STATUS);
    obj.direction = (String) result.get(FIELD_DIRECTION);
    obj.type = (String) result.get(FIELD_TYPE);
    obj.symbol = (String) result.get(FIELD_SYMBOL);
    obj.price = result.get(FIELD_PRICE) == null ? null : ((Double) result.get(FIELD_PRICE)).floatValue();
    obj.amount = (Double) result.get(FIELD_AMOUNT);
    obj.comment = (String) result.get(FIELD_COMMENT);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, orderId, positionId, orderTimestamp, status, direction, type, symbol, price, amount, comment);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, positionId, orderTimestamp, status, direction, type, symbol, price, amount, comment, orderId);
  }
}