package ru.rexchange.db.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.exception.SystemException;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBUtils {
  protected static final Logger LOGGER = LoggerFactory.getLogger(DBUtils.class);

	//TODO переделать под любой тип с использованием template
	//TODO переделать для использования параметров
	public static Integer getIntValue(Connection connection, String sql)
			throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
				//LOGGER.trace("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
		return -1;
	}

	public static Long getLongValue(Connection connection, String sql) throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.next()) {
					return rs.getLong(1);
				}
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
		return null;
	}

	public static String getStringValue(Connection connection, String sql) throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //System.out.println("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.next()) {
					return rs.getString(1);
				}
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
		return null;
	}

	public static List<Long> getLongList(Connection connection, String sql) throws SQLException {
		Statement stmt = null;
		try {
			List<Long> result = new ArrayList<>();
			// Execute a query
			synchronized (connection) {
				//System.out.println("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					result.add(rs.getLong(1));
				}
			}
			return result;
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	//todo сделать getAnyList не зависящий от типа колонки
	public static List<String> getStringList(Connection connection, String sql) throws SQLException {
		Statement stmt = null;
		try {
			List<String> result = new ArrayList<>();
			// Execute a query
			synchronized (connection) {
				//System.out.println("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					result.add(rs.getString(1));
				}
			}
			return result;
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}


	public static boolean hasRecords(Connection connection, String sql)
			throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				// Extract data from result set
				return rs.next();
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	public static boolean executeQuery(Connection connection, String sql)
			throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				return stmt.execute(sql);
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	public static boolean executeQuery(Connection connection, String sql, Object... params)
			throws SQLException {
		PreparedStatement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.prepareStatement(sql);
				for (int i = 0; i < params.length; i++) {
					setParameter(stmt, params[i], i + 1);
				}
				LOGGER.trace("Executing query:\n" + formatQuery(sql, params));
				return stmt.execute();
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	private static String formatQuery(String sql, Object[] params) {
		if (params == null || params.length == 0 || !sql.contains("?"))
			return sql;
		String result = sql;
		for (Object param : params) {
			result = result.replaceFirst("\\?", String.valueOf(param));
		}
		return result;
	}

	public static Map<String, String> getStringPairs(Connection connection, String sql)
      throws SQLException {
		Statement stmt = null;
		try {
			// Execute a query
			Map<String, String> result = new HashMap<>();
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.createStatement();
				LOGGER.trace("Executing query:\n" + sql);
				ResultSet rs = stmt.executeQuery(sql);
				// Extract data from result set
				while (rs.next()) {
					result.put(rs.getString(1).trim(), rs.getString(2).trim());
				}
			}
			return result;
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	public static Map<String, Object> getQueryResult(Connection connection, String sql, Object... params)
      throws SQLException {
		PreparedStatement stmt = null;
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.prepareStatement(sql);
				for (int i = 0; i < params.length; i++) {
					setParameter(stmt, params[i], i + 1);
				}
				LOGGER.trace("Executing query:\n" + formatQuery(sql, params));
				ResultSet rs = stmt.executeQuery();
				if (rs.next()) {
					Map<String, Object> result = new HashMap<>();
					ResultSetMetaData meta = rs.getMetaData();
					int colCount = meta.getColumnCount();
					for (int i = 0; i < colCount; i++) {
						String colName = meta.getColumnName(i + 1);
						result.put(colName, rs.getObject(colName));
					}
					return result;
				}
				return null;
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	public static List<Map<String, Object>> getQueryResults(Connection connection, String sql, Object... params)
      throws SQLException {
		PreparedStatement stmt = null;
		List<Map<String, Object>> result = new ArrayList<>();
		try {
			// Execute a query
			synchronized (connection) {
        //LOGGER.trace("Creating statement...");
				stmt = connection.prepareStatement(sql);
				for (int i = 0; i < params.length; i++) {
					setParameter(stmt, params[i], i + 1);
				}
				LOGGER.trace("Executing query:\n" + formatQuery(sql, params));
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					Map<String, Object> map = new HashMap<>();
					ResultSetMetaData meta = rs.getMetaData();
					int colCount = meta.getColumnCount();
					for (int i = 0; i < colCount; i++) {
						String colName = meta.getColumnName(i + 1);
						map.put(colName, rs.getObject(colName));
					}
					result.add(map);
				}
				return result;
			}
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				LOGGER.warn("Error occured while closing prepared statement", e);
			}
		}
	}

	public static long generateNext(Connection connection, String sequence) throws SQLException {
		try (PreparedStatement stmt = connection.prepareStatement(
				String.format("SELECT GEN_ID(%s, 1) FROM RDB$DATABASE", sequence))) {
			try (ResultSet rs = stmt.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}


  /*public static String quotateVariable(Object var) {
  	return quotateVariable(var, true);
  }
  
  public static String quotateVariable(Object var, boolean escape) {
  	if (var == null)
  		return "";
  	if (var instanceof String) {
  		// todo if (escape) исключить возможность инъекции
  		return String.format("'%s'", var);
  	}
  	if (var instanceof Timestamp) {
  		return String.format("cast('%s' as TIMESTAMP)",
  				DateUtils.formatDateTime((Date) var, "yyyy-MM-dd HH:mm:ss.SSS"));
  	}
  	if (var instanceof Date) {
  		return String.format("'%s'", DateUtils.formatDateTime((Date) var, "yyyy-MM-dd"));
  	}
  
  	return var.toString();
  }*/

	protected static void setParameter(PreparedStatement stmt, Object param, int number)
			throws SQLException {
		if (param == null) {
			stmt.setNull(number, Types.VARCHAR);
		} else if (param instanceof String) {
			stmt.setString(number, (String) param);
		} else if (param instanceof Long) {
			stmt.setLong(number, (Long) param);
		} else if (param instanceof Integer) {
			stmt.setInt(number, (Integer) param);
		} else if (param instanceof Double) {
			stmt.setDouble(number, (Double) param);
		} else if (param instanceof Float) {
			stmt.setFloat(number, (Float) param);
		} else if (param instanceof Timestamp) {
			stmt.setTimestamp(number, (Timestamp) param);
		} else if (param instanceof Date) {
			stmt.setDate(number, (Date) param);
		} else
			throw new SystemException("Unsupported variable type %s", param.getClass());
	}
}
