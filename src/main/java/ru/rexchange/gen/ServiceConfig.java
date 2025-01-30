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
public class ServiceConfig extends UserObject {
  public static final String TABLE_NAME = "SERVICE_CONFIG";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_NAME = "NAME";
  public static final String FIELD_TYPE = "TYPE";
  public static final String FIELD_TIME_FRAME = "TIME_FRAME";
  public static final String FIELD_MODEL_ID = "MODEL_ID";
  public static final String FIELD_ACTIVE = "ACTIVE";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, NAME, TYPE, TIME_FRAME, MODEL_ID, ACTIVE FROM SERVICE_CONFIG WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO SERVICE_CONFIG(ID, NAME, TYPE, TIME_FRAME, MODEL_ID, ACTIVE) VALUES (?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE SERVICE_CONFIG SET NAME = ?, TYPE = ?, TIME_FRAME = ?, MODEL_ID = ?, ACTIVE = ? WHERE ID = ?";
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

  protected Integer timeFrame;

  public Integer getTimeFrame() {
    return this.timeFrame;
  }

  public void setTimeFrame(Integer value) {
    this.timeFrame = value;
  }

  protected String modelId;

  public String getModelId() {
    return this.modelId;
  }

  public void setModelId(String value) {
    this.modelId = value;
  }

  protected Boolean active;

  public Boolean getActive() {
    return this.active;
  }

  public void setActive(Boolean value) {
    this.active = value;
  }

  public ServiceConfig() {
  }

  public ServiceConfig(String id) {
    this.id = id;
  }

  public static ServiceConfig createAndLoad(Connection conn, String id) throws SQLException, UserException, SystemException {
    ServiceConfig instance = new ServiceConfig();
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
      ServiceConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
    super.load(conn);
  }

  public static void fillFromResultSet(Map<String, Object> result, ServiceConfig obj) {
    obj.id = (String) result.get(FIELD_ID);
    obj.name = (String) result.get(FIELD_NAME);
    obj.type = (String) result.get(FIELD_TYPE);
    obj.timeFrame = (Integer) result.get(FIELD_TIME_FRAME);
    obj.modelId = (String) result.get(FIELD_MODEL_ID);
    obj.active = result.get(FIELD_ACTIVE) == null ? null : Objects.equals(result.get(FIELD_ACTIVE), 1);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn) && super.update(conn);
    } else {
      return update(conn) && super.update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, id, name, type, timeFrame, modelId, active);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, name, type, timeFrame, modelId, active, id);
  }
}