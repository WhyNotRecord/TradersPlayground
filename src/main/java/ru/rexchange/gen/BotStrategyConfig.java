package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;


@Entity
public class BotStrategyConfig {
  public static final String TABLE_NAME = "BOT_STRATEGY_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_TYPE = "TYPE";
  public static final String FIELD_SAFETY_ONLY = "SAFETY_ONLY";
  public static final String FIELD_BOT_ID = "BOT_ID";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, TYPE, SAFETY_ONLY, BOT_ID FROM BOT_STRATEGY_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO BOT_STRATEGY_CONFIG(ID, TYPE, SAFETY_ONLY, BOT_ID) VALUES (?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE BOT_STRATEGY_CONFIG SET TYPE = ?, SAFETY_ONLY = ?, BOT_ID = ? WHERE ID = ?";
  private static final String QUERY_ID_SEQ_VALUE = "SELECT gen_id(SEQ_BOT_STRATEGY_CONFIG_ID, 1) FROM rdb$database";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long id;//primary key

  public Long getId() {
    return this.id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  protected String type;

  public String getType() {
    return this.type;
  }

  public void setType(String value) {
    this.type = value;
  }

  protected Boolean safetyOnly;

  public Boolean getSafetyOnly() {
    return this.safetyOnly;
  }

  public void setSafetyOnly(Boolean value) {
    this.safetyOnly = value;
  }

  protected String botId;

  public String getBotId() {
    return this.botId;
  }

  public void setBotId(String value) {
    this.botId = value;
  }

  public BotStrategyConfig() {
  }

  public BotStrategyConfig(Long id) {
    this.id = id;
  }

  public static BotStrategyConfig createAndLoad(Connection conn, Long id) throws SQLException, UserException, SystemException {
    BotStrategyConfig instance = new BotStrategyConfig();
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
      BotStrategyConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, BotStrategyConfig obj) {
    obj.id = (Long) result.get(FIELD_ID);
    obj.type = (String) result.get(FIELD_TYPE);
    obj.safetyOnly = result.get(FIELD_SAFETY_ONLY) == null ? null : Objects.equals(result.get(FIELD_SAFETY_ONLY), 1);
    obj.botId = (String) result.get(FIELD_BOT_ID);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    this.id = DBUtils.getLongValue(conn, QUERY_ID_SEQ_VALUE);
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, type, safetyOnly, botId);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, type, safetyOnly, botId, id);
  }
}