package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;


@Entity
public class UserObject {
  public static final String TABLE_NAME = "USER_OBJECT";
  public static final String FIELD_ID = "ID";
  public static final String FIELD_CREATED_BY = "CREATED_BY";
  public static final String FIELD_UPDATED_BY = "UPDATED_BY";
  public static final String FIELD_CREATED_TS = "CREATED_TS";
  public static final String FIELD_UPDATED_TS = "UPDATED_TS";
  public static final String FIELD_UPDATED = "UPDATED";
  private static final String QUERY_LOAD_OBJECT = "SELECT ID, CREATED_BY, UPDATED_BY, CREATED_TS, UPDATED_TS, UPDATED FROM %s WHERE ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO %s(ID, CREATED_BY, UPDATED_BY, CREATED_TS, UPDATED_TS, UPDATED) VALUES (?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE %s SET CREATED_BY = ?, UPDATED_BY = ?, CREATED_TS = ?, UPDATED_TS = ?, UPDATED = ? WHERE ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String id;//primary key

  public String getId() {
    return this.id;
  }

  public void setId(String value) {
    this.id = value;
  }

  protected Long createdBy;

  public Long getCreatedBy() {
    return this.createdBy;
  }

  public void setCreatedBy(Long value) {
    this.createdBy = value;
  }

  protected Long updatedBy;

  public Long getUpdatedBy() {
    return this.updatedBy;
  }

  public void setUpdatedBy(Long value) {
    this.updatedBy = value;
  }

  protected Timestamp createdTs;

  public Timestamp getCreatedTs() {
    return this.createdTs;
  }

  public void setCreatedTs(Timestamp value) {
    this.createdTs = value;
  }

  protected Timestamp updatedTs;

  public Timestamp getUpdatedTs() {
    return this.updatedTs;
  }

  public void setUpdatedTs(Timestamp value) {
    this.updatedTs = value;
  }

  protected Boolean updated;

  public Boolean getUpdated() {
    return this.updated;
  }

  public void setUpdated(Boolean value) {
    this.updated = value;
  }

  public UserObject() {
  }

  public UserObject(String id) {
    this.id = id;
  }

  public static UserObject createAndLoad(Connection conn, String id) throws SQLException, UserException, SystemException {
    UserObject instance = new UserObject();
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
    Map<String, Object> result = DBUtils.getQueryResult(conn, String.format(QUERY_LOAD_OBJECT, getTableName()), id);
    if (result != null) {
      UserObject.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.id);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, UserObject obj) {
    obj.id = (String) result.get(FIELD_ID);
    obj.createdBy = (Long) result.get(FIELD_CREATED_BY);
    obj.updatedBy = (Long) result.get(FIELD_UPDATED_BY);
    obj.createdTs = (Timestamp) result.get(FIELD_CREATED_TS);
    obj.updatedTs = (Timestamp) result.get(FIELD_UPDATED_TS);
    obj.updated = result.get(FIELD_UPDATED) == null ? null : Objects.equals(result.get(FIELD_UPDATED), 1);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    throw new UnsupportedOperationException("Parent object cannot be inserted");
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, String.format(QUERY_UPDATE_OBJECT, getTableName()), createdBy, updatedBy, createdTs, updatedTs, updated, id);
  }
}