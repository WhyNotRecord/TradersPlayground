package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class ModelConfig extends UserObject {
  public static final String TABLE_NAME = "MODEL_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_NAME = "NAME";
  public static final String FIELD_TYPE = "TYPE";
  public static final String FIELD_SYMBOL_ID = "SYMBOL_ID";
  public static final String FIELD_CAPACITY = "CAPACITY";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, NAME, TYPE, SYMBOL_ID, CAPACITY FROM MODEL_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO MODEL_CONFIG(ID, NAME, TYPE, SYMBOL_ID, CAPACITY) VALUES (?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE MODEL_CONFIG SET NAME = ?, TYPE = ?, SYMBOL_ID = ?, CAPACITY = ? WHERE ID = ?";
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

  protected Long symbolId;

  public Long getSymbolId() {
    return this.symbolId;
  }

  public void setSymbolId(Long value) {
    this.symbolId = value;
  }

  protected Integer capacity;

  public Integer getCapacity() {
    return this.capacity;
  }

  public void setCapacity(Integer value) {
    this.capacity = value;
  }

  public ModelConfig() {
  }

  public ModelConfig(String id) {
    this.id = id;
  }

  public static ModelConfig createAndLoad(Connection conn, String id) throws SQLException, UserException, SystemException {
    ModelConfig instance = new ModelConfig();
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
      ModelConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
    super.load(conn);
  }

  public static void fillFromResultSet(Map<String, Object> result, ModelConfig obj) {
    obj.id = (String) result.get(FIELD_ID);
    obj.name = (String) result.get(FIELD_NAME);
    obj.type = (String) result.get(FIELD_TYPE);
    obj.symbolId = (Long) result.get(FIELD_SYMBOL_ID);
    obj.capacity = (Integer) result.get(FIELD_CAPACITY);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn) && super.update(conn);
    } else {
      return update(conn) && super.update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, name, type, symbolId, capacity);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, name, type, symbolId, capacity, id);
  }
}