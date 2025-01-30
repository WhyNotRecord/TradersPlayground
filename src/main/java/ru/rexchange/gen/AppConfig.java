package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class AppConfig {
  public static final String TABLE_NAME = "APP_CONFIG";
  public static final String FIELD_CONFIG_ID = "CONFIG_ID";
  public static final String FIELD_PASSWORD_DEFAULT = "PASSWORD_DEFAULT";
  public static final String FIELD_TG_TOKEN = "TG_TOKEN";
  public static final String FIELD_TEMP_DIR_PATH = "TEMP_DIR_PATH";
  public static final String FIELD_ML_MODELS_PATH = "ML_MODELS_PATH";
  public static final String FIELD_MAIL_LOGIN_DEFAULT = "MAIL_LOGIN_DEFAULT";
  public static final String FIELD_MAIL_PASS_DEFAULT = "MAIL_PASS_DEFAULT";
  private static final String QUERY_LOAD_OBJECT = "SELECT CONFIG_ID, PASSWORD_DEFAULT, TG_TOKEN, TEMP_DIR_PATH, ML_MODELS_PATH, MAIL_LOGIN_DEFAULT, MAIL_PASS_DEFAULT FROM APP_CONFIG WHERE CONFIG_ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO APP_CONFIG(CONFIG_ID, PASSWORD_DEFAULT, TG_TOKEN, TEMP_DIR_PATH, ML_MODELS_PATH, MAIL_LOGIN_DEFAULT, MAIL_PASS_DEFAULT) VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE APP_CONFIG SET PASSWORD_DEFAULT = ?, TG_TOKEN = ?, TEMP_DIR_PATH = ?, ML_MODELS_PATH = ?, MAIL_LOGIN_DEFAULT = ?, MAIL_PASS_DEFAULT = ? WHERE CONFIG_ID = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long configId;//primary key

  public Long getConfigId() {
    return this.configId;
  }

  public void setConfigId(Long value) {
    this.configId = value;
  }

  protected String passwordDefault;

  public String getPasswordDefault() {
    return this.passwordDefault;
  }

  public void setPasswordDefault(String value) {
    this.passwordDefault = value;
  }

  protected String tgToken;

  public String getTgToken() {
    return this.tgToken;
  }

  public void setTgToken(String value) {
    this.tgToken = value;
  }

  protected String tempDirPath;

  public String getTempDirPath() {
    return this.tempDirPath;
  }

  public void setTempDirPath(String value) {
    this.tempDirPath = value;
  }

  protected String mlModelsPath;

  public String getMlModelsPath() {
    return this.mlModelsPath;
  }

  public void setMlModelsPath(String value) {
    this.mlModelsPath = value;
  }

  protected String mailLoginDefault;

  public String getMailLoginDefault() {
    return this.mailLoginDefault;
  }

  public void setMailLoginDefault(String value) {
    this.mailLoginDefault = value;
  }

  protected String mailPassDefault;

  public String getMailPassDefault() {
    return this.mailPassDefault;
  }

  public void setMailPassDefault(String value) {
    this.mailPassDefault = value;
  }

  public AppConfig() {
  }

  public AppConfig(Long configId) {
    this.configId = configId;
  }

  public static AppConfig createAndLoad(Connection conn, Long configId) throws SQLException, UserException, SystemException {
    AppConfig instance = new AppConfig();
    instance.configId = configId;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (configId == null)
      throw new SystemException("Primary key (configId) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, configId);
    if (result != null) {
      AppConfig.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.configId);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, AppConfig obj) {
    obj.configId = (Long) result.get(FIELD_CONFIG_ID);
    obj.passwordDefault = (String) result.get(FIELD_PASSWORD_DEFAULT);
    obj.tgToken = (String) result.get(FIELD_TG_TOKEN);
    obj.tempDirPath = (String) result.get(FIELD_TEMP_DIR_PATH);
    obj.mlModelsPath = (String) result.get(FIELD_ML_MODELS_PATH);
    obj.mailLoginDefault = (String) result.get(FIELD_MAIL_LOGIN_DEFAULT);
    obj.mailPassDefault = (String) result.get(FIELD_MAIL_PASS_DEFAULT);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, configId, passwordDefault, tgToken, tempDirPath, mlModelsPath, mailLoginDefault, mailPassDefault);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, passwordDefault, tgToken, tempDirPath, mlModelsPath, mailLoginDefault, mailPassDefault, configId);
  }
}