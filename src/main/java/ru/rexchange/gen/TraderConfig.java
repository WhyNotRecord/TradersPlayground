package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class TraderConfig extends UserObject {
  public static final String TABLE_NAME = "TRADER_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_NAME = "NAME";
  public static final String FIELD_TYPE = "TYPE";
  public static final String FIELD_API_ID = "API_ID";
  public static final String FIELD_DEAL_AMOUNT_TYPE = "DEAL_AMOUNT_TYPE";
  public static final String FIELD_DEAL_AMOUNT_SIZE = "DEAL_AMOUNT_SIZE";
  public static final String FIELD_BOT_ID = "BOT_ID";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, NAME, TYPE, API_ID, DEAL_AMOUNT_TYPE, DEAL_AMOUNT_SIZE, BOT_ID FROM TRADER_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO TRADER_CONFIG(ID, NAME, TYPE, API_ID, DEAL_AMOUNT_TYPE, DEAL_AMOUNT_SIZE, BOT_ID) VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE TRADER_CONFIG SET NAME = ?, TYPE = ?, API_ID = ?, DEAL_AMOUNT_TYPE = ?, DEAL_AMOUNT_SIZE = ?, BOT_ID = ? WHERE ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String name;

  public String getName() {
    return this.name;
  }

  public void setName(String value) {
    this.name = value;
  }

  protected String type;

  public String getType() {
    return this.type;
  }

  public void setType(String value) {
    this.type = value;
  }

  protected String apiId;

  public String getApiId() {
    return this.apiId;
  }

  public void setApiId(String value) {
    this.apiId = value;
  }

  protected String dealAmountType;

  public String getDealAmountType() {
    return this.dealAmountType;
  }

  public void setDealAmountType(String value) {
    this.dealAmountType = value;
  }

  protected Float dealAmountSize;

  public Float getDealAmountSize() {
    return this.dealAmountSize;
  }

  public void setDealAmountSize(Float value) {
    this.dealAmountSize = value;
  }

  protected String botId;

  public String getBotId() {
    return this.botId;
  }

  public void setBotId(String value) {
    this.botId = value;
  }

  public TraderConfig() {
  }

  public TraderConfig(String id) {
    this.id = id;
  }

  public static TraderConfig createAndLoad(Connection conn, String id) throws SQLException, UserException, SystemException {
    TraderConfig instance = new TraderConfig();
    instance.id = id;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (id == null)
      throw new SystemException("Primary key (id) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, id);
    if (result != null) {
      TraderConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
    super.load(conn);
  }

  public static void fillFromResultSet(Map<String, Object> result, TraderConfig obj) {
    obj.id = (String) result.get(FIELD_ID);
    obj.name = (String) result.get(FIELD_NAME);
    obj.type = (String) result.get(FIELD_TYPE);
    obj.apiId = (String) result.get(FIELD_API_ID);
    obj.dealAmountType = (String) result.get(FIELD_DEAL_AMOUNT_TYPE);
    obj.dealAmountSize = result.get(FIELD_DEAL_AMOUNT_SIZE) == null ? null : ((Double) result.get(FIELD_DEAL_AMOUNT_SIZE)).floatValue();
    obj.botId = (String) result.get(FIELD_BOT_ID);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn) && super.update(conn);
    } else {
      return update(conn) && super.update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, name, type, apiId, dealAmountType, dealAmountSize, botId);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, name, type, apiId, dealAmountType, dealAmountSize, botId, id);
  }
}