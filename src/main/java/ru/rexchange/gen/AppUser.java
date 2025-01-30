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
public class AppUser {
  public static final String TABLE_NAME = "APP_USER";
  public static final String FIELD_USER_ID = "USER_ID";
  public static final String FIELD_USER_LOGIN = "USER_LOGIN";
  public static final String FIELD_USER_NAME = "USER_NAME";
  public static final String FIELD_USER_TIER = "USER_TIER";
  public static final String FIELD_TG_CHAT_ID = "TG_CHAT_ID";
  public static final String FIELD_TG_USER_NAME = "TG_USER_NAME";
  public static final String FIELD_USER_HASH = "USER_HASH";
  public static final String FIELD_LAST_LOGIN = "LAST_LOGIN";
  public static final String FIELD_CREATED_TS = "CREATED_TS";
  public static final String FIELD_UPDATED_TS = "UPDATED_TS";
  private static final String QUERY_LOAD_OBJECT = "SELECT USER_ID, USER_LOGIN, USER_NAME, USER_TIER, TG_CHAT_ID, TG_USER_NAME, USER_HASH, LAST_LOGIN, CREATED_TS, UPDATED_TS FROM APP_USER WHERE USER_ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO APP_USER(USER_ID, USER_LOGIN, USER_NAME, USER_TIER, TG_CHAT_ID, TG_USER_NAME, USER_HASH, LAST_LOGIN, CREATED_TS, UPDATED_TS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE APP_USER SET USER_LOGIN = ?, USER_NAME = ?, USER_TIER = ?, TG_CHAT_ID = ?, TG_USER_NAME = ?, USER_HASH = ?, LAST_LOGIN = ?, CREATED_TS = ?, UPDATED_TS = ? WHERE USER_ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long userId;//primary key

  public Long getUserId() {
    return this.userId;
  }

  public void setUserId(Long value) {
    this.userId = value;
  }

  protected String userLogin;

  public String getUserLogin() {
    return this.userLogin;
  }

  public void setUserLogin(String value) {
    this.userLogin = value;
  }

  protected String userName;

  public String getUserName() {
    return this.userName;
  }

  public void setUserName(String value) {
    this.userName = value;
  }

  protected String userTier;

  public String getUserTier() {
    return this.userTier;
  }

  public void setUserTier(String value) {
    this.userTier = value;
  }

  protected String tgChatId;

  public String getTgChatId() {
    return this.tgChatId;
  }

  public void setTgChatId(String value) {
    this.tgChatId = value;
  }

  protected String tgUserName;

  public String getTgUserName() {
    return this.tgUserName;
  }

  public void setTgUserName(String value) {
    this.tgUserName = value;
  }

  protected String userHash;

  public String getUserHash() {
    return this.userHash;
  }

  public void setUserHash(String value) {
    this.userHash = value;
  }

  protected Timestamp lastLogin;

  public Timestamp getLastLogin() {
    return this.lastLogin;
  }

  public void setLastLogin(Timestamp value) {
    this.lastLogin = value;
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

  public AppUser() {
  }

  public AppUser(Long userId) {
    this.userId = userId;
  }

  public static AppUser createAndLoad(Connection conn, Long userId) throws SQLException, UserException, SystemException {
    AppUser instance = new AppUser();
    instance.userId = userId;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (userId == null)
      throw new SystemException("Primary key (userId) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, userId);
    if (result != null) {
      AppUser.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.userId);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, AppUser obj) {
    obj.userId = (Long) result.get(FIELD_USER_ID);
    obj.userLogin = (String) result.get(FIELD_USER_LOGIN);
    obj.userName = (String) result.get(FIELD_USER_NAME);
    obj.userTier = (String) result.get(FIELD_USER_TIER);
    obj.tgChatId = (String) result.get(FIELD_TG_CHAT_ID);
    obj.tgUserName = (String) result.get(FIELD_TG_USER_NAME);
    obj.userHash = (String) result.get(FIELD_USER_HASH);
    obj.lastLogin = (Timestamp) result.get(FIELD_LAST_LOGIN);
    obj.createdTs = (Timestamp) result.get(FIELD_CREATED_TS);
    obj.updatedTs = (Timestamp) result.get(FIELD_UPDATED_TS);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, userId, userLogin, userName, userTier, tgChatId, tgUserName, userHash, lastLogin, createdTs, updatedTs);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, userLogin, userName, userTier, tgChatId, tgUserName, userHash, lastLogin, createdTs, updatedTs, userId);
  }
}