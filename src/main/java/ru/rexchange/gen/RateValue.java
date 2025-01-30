package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class RateValue {
  public static final String TABLE_NAME = "RATE_VALUE";
  public static final String FIELD_SYMBOL_ID = "SYMBOL_ID";
  public static final String FIELD_RATE_TIMESTAMP = "RATE_TIMESTAMP";
  public static final String FIELD_RATE_VALUE = "RATE_VALUE";
  private static final String QUERY_LOAD_OBJECT = "SELECT SYMBOL_ID, RATE_TIMESTAMP, RATE_VALUE FROM RATE_VALUE WHERE SYMBOL_ID = ? AND RATE_TIMESTAMP = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO RATE_VALUE(SYMBOL_ID, RATE_TIMESTAMP, RATE_VALUE) VALUES (?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE RATE_VALUE SET RATE_VALUE = ? WHERE SYMBOL_ID = ? AND RATE_TIMESTAMP = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long symbolId;//primary key

  public Long getSymbolId() {
    return this.symbolId;
  }

  public void setSymbolId(Long value) {
    this.symbolId = value;
  }

  protected Long rateTimestamp;//primary key

  public Long getRateTimestamp() {
    return this.rateTimestamp;
  }

  public void setRateTimestamp(Long value) {
    this.rateTimestamp = value;
  }

  protected Float rateValue;

  public Float getRateValue() {
    return this.rateValue;
  }

  public void setRateValue(Float value) {
    this.rateValue = value;
  }

  public RateValue() {
  }

  public RateValue(Long symbolId, Long rateTimestamp) {
    this.symbolId = symbolId;
    this.rateTimestamp = rateTimestamp;
  }

  public static RateValue createAndLoad(Connection conn, Long symbolId, Long rateTimestamp) throws SQLException, UserException, SystemException {
    RateValue instance = new RateValue();
    instance.symbolId = symbolId;
    instance.rateTimestamp = rateTimestamp;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (symbolId == null)
      throw new SystemException("Primary key (symbolId) is null");
    if (rateTimestamp == null)
      throw new SystemException("Primary key (rateTimestamp) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, symbolId, rateTimestamp);
    if (result != null) {
      RateValue.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s, %s)", this.symbolId, this.rateTimestamp);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, RateValue obj) {
    obj.symbolId = (Long) result.get(FIELD_SYMBOL_ID);
    obj.rateTimestamp = (Long) result.get(FIELD_RATE_TIMESTAMP);
    obj.rateValue = result.get(FIELD_RATE_VALUE) == null ? null : ((Double) result.get(FIELD_RATE_VALUE)).floatValue();
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, symbolId, rateTimestamp, rateValue);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, rateValue, symbolId, rateTimestamp);
  }
}