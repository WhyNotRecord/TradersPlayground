package ru.rexchange.gen;

import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import javax.persistence.Entity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;


@Entity
public class SymbolInfo {
  public static final String TABLE_NAME = "SYMBOL_INFO";
  public static final String FIELD_SYMBOL_ID = "SYMBOL_ID";
  public static final String FIELD_SYMBOL_NAME = "SYMBOL_NAME";
  public static final String FIELD_SYMBOL_DESCRIPTION = "SYMBOL_DESCRIPTION";
  public static final String FIELD_BASE_IND = "BASE_IND";
  public static final String FIELD_QUOT_IND = "QUOT_IND";
  private static final String QUERY_LOAD_OBJECT = "SELECT SYMBOL_ID, SYMBOL_NAME, SYMBOL_DESCRIPTION, BASE_IND, QUOT_IND FROM SYMBOL_INFO WHERE SYMBOL_ID = ?";
  private static final String QUERY_INSERT_OBJECT = "INSERT INTO SYMBOL_INFO(SYMBOL_ID, SYMBOL_NAME, SYMBOL_DESCRIPTION, BASE_IND, QUOT_IND) VALUES (?, ?, ?, ?, ?)";
  private static final String QUERY_UPDATE_OBJECT = "UPDATE SYMBOL_INFO SET SYMBOL_NAME = ?, SYMBOL_DESCRIPTION = ?, BASE_IND = ?, QUOT_IND = ? WHERE SYMBOL_ID = ?";
  private static final String QUERY_SYMBOL_ID_SEQ_VALUE = "SELECT gen_id(SEQ_SYMBOL_INFO_SYMBOL_ID, 1) FROM rdb$database";
  private boolean isNew = true;
  public boolean isNew() { return isNew; }

  protected Long symbolId;//primary key

  public Long getSymbolId() {
    return this.symbolId;
  }

  public void setSymbolId(Long value) {
    this.symbolId = value;
  }

  protected String symbolName;

  public String getSymbolName() {
    return this.symbolName;
  }

  public void setSymbolName(String value) {
    this.symbolName = value;
  }

  protected String symbolDescription;

  public String getSymbolDescription() {
    return this.symbolDescription;
  }

  public void setSymbolDescription(String value) {
    this.symbolDescription = value;
  }

  protected String baseInd;

  public String getBaseInd() {
    return this.baseInd;
  }

  public void setBaseInd(String value) {
    this.baseInd = value;
  }

  protected String quotInd;

  public String getQuotInd() {
    return this.quotInd;
  }

  public void setQuotInd(String value) {
    this.quotInd = value;
  }

  public SymbolInfo() {
  }

  public SymbolInfo(Long symbolId) {
    this.symbolId = symbolId;
  }

  public static SymbolInfo createAndLoad(Connection conn, Long symbolId) throws SQLException, UserException, SystemException {
    SymbolInfo instance = new SymbolInfo();
    instance.symbolId = symbolId;
    instance.load(conn);
    return instance;
  }

  public String getTableName() {
    return TABLE_NAME;
  }

  public void load(Connection conn) throws SQLException, UserException, SystemException {
    if (symbolId == null)
      throw new SystemException("Primary key (symbolId) is null");
    Map<String, Object> result = DBUtils.getQueryResult(conn, QUERY_LOAD_OBJECT, symbolId);
    if (result != null) {
      SymbolInfo.fillFromResultSet(result, this);
      isNew = false;
    } else {
      throw new UserException("Cannot find object (%s)", this.symbolId);
    }
  }

  public static void fillFromResultSet(Map<String, Object> result, SymbolInfo obj) {
    obj.symbolId = (Long) result.get(FIELD_SYMBOL_ID);
    obj.symbolName = (String) result.get(FIELD_SYMBOL_NAME);
    obj.symbolDescription = (String) result.get(FIELD_SYMBOL_DESCRIPTION);
    obj.baseInd = (String) result.get(FIELD_BASE_IND);
    obj.quotInd = (String) result.get(FIELD_QUOT_IND);
  }

  public boolean save(Connection conn) throws SQLException {
    if (isNew) {
      return insert(conn);
    } else {
      return update(conn);
    }
  }

  public boolean insert(Connection conn) throws SQLException {
    this.symbolId = DBUtils.getLongValue(conn, QUERY_SYMBOL_ID_SEQ_VALUE);
    boolean result = DBUtils.executeQuery(conn, QUERY_INSERT_OBJECT, symbolId, symbolName, symbolDescription, baseInd, quotInd);
    isNew = false;
    return result;
  }

  public boolean update(Connection conn) throws SQLException {
    return DBUtils.executeQuery(conn, QUERY_UPDATE_OBJECT, symbolName, symbolDescription, baseInd, quotInd, symbolId);
  }
}