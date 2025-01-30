package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class ParameterConfig {
  public static final String TABLE_NAME = "PARAMETER_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_PARAM_NAME = "PARAM_NAME";
  public static final String FIELD_PARAM_VALUE = "PARAM_VALUE";
  public static final String FIELD_OWNER_ID = "OWNER_ID";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, PARAM_NAME, PARAM_VALUE, OWNER_ID FROM PARAMETER_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO PARAMETER_CONFIG(ID, PARAM_NAME, PARAM_VALUE, OWNER_ID) VALUES (?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE PARAMETER_CONFIG SET PARAM_NAME = ?, PARAM_VALUE = ?, OWNER_ID = ? WHERE ID = ?";
  private static final String QUERY_ID_SEQ_VALUE = "SELECT gen_id(SEQ_PARAMETER_CONFIG_ID, 1) FROM rdb$database";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long id;//primary key

  public Long getId() {
    return this.id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  protected String paramName;

  public String getParamName() {
    return this.paramName;
  }

  public void setParamName(String value) {
    this.paramName = value;
  }

  protected String paramValue;

  public String getParamValue() {
    return this.paramValue;
  }

  public void setParamValue(String value) {
    this.paramValue = value;
  }

  protected String ownerId;

  public String getOwnerId() {
    return this.ownerId;
  }

  public void setOwnerId(String value) {
    this.ownerId = value;
  }

  public ParameterConfig() {
  }

  public ParameterConfig(Long id) {
    this.id = id;
  }

  public static ParameterConfig createAndLoad(Connection conn, Long id) throws SQLException, UserException, SystemException {
    ParameterConfig instance = new ParameterConfig();
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
      ParameterConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, ParameterConfig obj) {
    obj.id = (Long) result.get(FIELD_ID);
    obj.paramName = (String) result.get(FIELD_PARAM_NAME);
    obj.paramValue = (String) result.get(FIELD_PARAM_VALUE);
    obj.ownerId = (String) result.get(FIELD_OWNER_ID);
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
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, paramName, paramValue, ownerId);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, paramName, paramValue, ownerId, id);
  }
}