package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class IndexInfo {
  public static final String TABLE_NAME = "INDEX_INFO";
  public static final String FIELD_INDEX_CODE = "INDEX_CODE";
  public static final String FIELD_INDEX_NAME = "INDEX_NAME";
  private static final String QUERY_LOAD_OBJECT = "SELECT INDEX_CODE, INDEX_NAME FROM INDEX_INFO WHERE INDEX_CODE = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO INDEX_INFO(INDEX_CODE, INDEX_NAME) VALUES (?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE INDEX_INFO SET INDEX_NAME = ? WHERE INDEX_CODE = ?";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected String indexCode;//primary key

  public String getIndexCode() {
    return this.indexCode;
  }

  public void setIndexCode(String value) {
    this.indexCode = value;
  }

  protected String indexName;

  public String getIndexName() {
    return this.indexName;
  }

  public void setIndexName(String value) {
    this.indexName = value;
  }

  public IndexInfo() {
  }

  public IndexInfo(String indexCode) {
    this.indexCode = indexCode;
  }

  public static IndexInfo createAndLoad(Connection conn, String indexCode) throws SQLException, UserException, SystemException {
    IndexInfo instance = new IndexInfo();
    instance.indexCode = indexCode;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (indexCode == null)
      throw new SystemException("Primary key (indexCode) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, indexCode);
    if (result != null) {
      IndexInfo.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.indexCode);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, IndexInfo obj) {
    obj.indexCode = (String) result.get(FIELD_INDEX_CODE);
    obj.indexName = (String) result.get(FIELD_INDEX_NAME);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, indexCode, indexName);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, indexName, indexCode);
  }
}