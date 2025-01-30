package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class ApiConfig extends UserObject {
  public static final String TABLE_NAME = "API_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_API_TYPE = "API_TYPE";
  public static final String FIELD_API_NAME = "API_NAME";
  public static final String FIELD_API_PUBLIC_KEY = "API_PUBLIC_KEY";
  public static final String FIELD_API_PRIVATE_KEY = "API_PRIVATE_KEY";
  public static final String FIELD_API_PERSONAL_KEY = "API_PERSONAL_KEY";
  public static final String FIELD_API_DESCRIPTION = "API_DESCRIPTION";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, API_TYPE, API_NAME, API_PUBLIC_KEY, API_PRIVATE_KEY, API_PERSONAL_KEY, API_DESCRIPTION FROM API_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO API_CONFIG(ID, API_TYPE, API_NAME, API_PUBLIC_KEY, API_PRIVATE_KEY, API_PERSONAL_KEY, API_DESCRIPTION) VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE API_CONFIG SET API_TYPE = ?, API_NAME = ?, API_PUBLIC_KEY = ?, API_PRIVATE_KEY = ?, API_PERSONAL_KEY = ?, API_DESCRIPTION = ? WHERE ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String apiType;

  public String getApiType() {
    return this.apiType;
  }

  public void setApiType(String value) {
    this.apiType = value;
  }

  protected String apiName;

  public String getApiName() {
    return this.apiName;
  }

  public void setApiName(String value) {
    this.apiName = value;
  }

  protected String apiPublicKey;

  public String getApiPublicKey() {
    return this.apiPublicKey;
  }

  public void setApiPublicKey(String value) {
    this.apiPublicKey = value;
  }

  protected String apiPrivateKey;

  public String getApiPrivateKey() {
    return this.apiPrivateKey;
  }

  public void setApiPrivateKey(String value) {
    this.apiPrivateKey = value;
  }

  protected String apiPersonalKey;

  public String getApiPersonalKey() {
    return this.apiPersonalKey;
  }

  public void setApiPersonalKey(String value) {
    this.apiPersonalKey = value;
  }

  protected String apiDescription;

  public String getApiDescription() {
    return this.apiDescription;
  }

  public void setApiDescription(String value) {
    this.apiDescription = value;
  }

  public ApiConfig() {
  }

  public ApiConfig(String id) {
    this.id = id;
  }

  public static ApiConfig createAndLoad(Connection conn, String id) throws SQLException, UserException, SystemException {
    ApiConfig instance = new ApiConfig();
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
      ApiConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
    super.load(conn);
  }

  public static void fillFromResultSet(Map<String, Object> result, ApiConfig obj) {
    obj.id = (String) result.get(FIELD_ID);
    obj.apiType = (String) result.get(FIELD_API_TYPE);
    obj.apiName = (String) result.get(FIELD_API_NAME);
    obj.apiPublicKey = (String) result.get(FIELD_API_PUBLIC_KEY);
    obj.apiPrivateKey = (String) result.get(FIELD_API_PRIVATE_KEY);
    obj.apiPersonalKey = (String) result.get(FIELD_API_PERSONAL_KEY);
    obj.apiDescription = (String) result.get(FIELD_API_DESCRIPTION);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn) && super.update(conn);
    } else {
      return update(conn) && super.update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, apiType, apiName, apiPublicKey, apiPrivateKey, apiPersonalKey, apiDescription);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, apiType, apiName, apiPublicKey, apiPrivateKey, apiPersonalKey, apiDescription, id);
  }
}