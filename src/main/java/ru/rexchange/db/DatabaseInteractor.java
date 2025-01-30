package ru.rexchange.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.storage.RateCandle;
import ru.rexchange.db.tools.DBUtils;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;
import ru.rexchange.gen.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

public class DatabaseInteractor {
  protected static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInteractor.class);

  private static Connection getConnection() throws ClassNotFoundException, SQLException {
    return null;
  }

  @Deprecated
  public static boolean newerRateValueExists(RateCandle rateValue, String baseCurrency, String quotedCurrency) {
    try (Connection conn = getConnection()) {
      SymbolInfo rate = getRateData(conn, baseCurrency, quotedCurrency);

      return DBUtils.hasRecords(conn,
          String.format("SELECT FIRST 1 %s FROM %s RT WHERE RT.%s = %s AND RT.%s >= %s",
              RateValue.FIELD_RATE_VALUE, RateValue.TABLE_NAME,
              RateValue.FIELD_SYMBOL_ID, rate.getSymbolId(),
              RateValue.FIELD_RATE_TIMESTAMP, rateValue.getCloseTime()));
    } catch (Exception e) {
      LOGGER.error("Unsuccessful query execution", e);
      return false;
    }
  }

  private static SymbolInfo getRateData(Connection conn, String baseCurrency, String quotedCurrency) throws SQLException {
    SymbolInfo rate = loadRateByName(conn, baseCurrency, quotedCurrency);
    if (rate == null) {
      rate = new SymbolInfo();
      rate.setBaseInd(baseCurrency);
      rate.setQuotInd(quotedCurrency);
      rate.setSymbolName(baseCurrency + quotedCurrency);
      rate.save(conn);
    }
    return rate;
  }

  private static SymbolInfo loadRateByName(Connection conn, String baseCurrency, String quotedCurrency) throws SQLException {
    Long id = DBUtils.getLongValue(conn,
        String.format("SELECT FIRST 1 %s FROM %s RT WHERE RT.%s = '%s' AND RT.%s = '%s'",
            SymbolInfo.FIELD_SYMBOL_ID, SymbolInfo.TABLE_NAME,
            SymbolInfo.FIELD_BASE_IND, baseCurrency, SymbolInfo.FIELD_QUOT_IND, quotedCurrency));
    if (id == null)
      return null;
    SymbolInfo result = new SymbolInfo();
    result.setSymbolId(id);
    result.load(conn);
    return result;
  }


  public static List<ModelConfig> getModelsConfigs() {
    try (Connection conn = getConnection()) {
      List<ModelConfig> results = new ArrayList<>();
      List<String> ids = DBUtils.getStringList(conn,
          String.format("SELECT MC.%s FROM %s MC", ModelConfig.FIELD_ID, ModelConfig.TABLE_NAME));
      for (String id : ids) {
        ModelConfig model = new ModelConfig();
        model.setId(id);
        model.load(conn);
        results.add(model);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<TraderConfig> getTradersConfigs(boolean updatedOnly) {
    try (Connection conn = getConnection()) {
      List<TraderConfig> results = new ArrayList<>();
      String sql = String.format("SELECT TC.%s FROM %s TC", TraderConfig.FIELD_ID, TraderConfig.TABLE_NAME);
      if (updatedOnly) {
        sql += " WHERE 1 = TC." + TraderConfig.FIELD_UPDATED;
      }
      List<String> ids = DBUtils.getStringList(conn, sql);
      for (String id : ids) {
        TraderConfig trader = new TraderConfig();
        trader.setId(id);
        trader.load(conn);
        results.add(trader);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<BotConfig> getBotsConfigs(boolean updatedOnly) {
    try (Connection conn = getConnection()) {
      List<BotConfig> results = new ArrayList<>();
      String sql = String.format("SELECT BC.%s FROM %s BC", BotConfig.FIELD_ID, BotConfig.TABLE_NAME);
      if (updatedOnly) {
        sql += " WHERE 1 = BC." + BotConfig.FIELD_UPDATED;
      }
      List<String> ids = DBUtils.getStringList(conn, sql);
      for (String id : ids) {
        BotConfig bot = new BotConfig();
        bot.setId(id);
        bot.load(conn);
        results.add(bot);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<ParameterConfig> getParamsConfigs(String ownerId) {
    try (Connection conn = getConnection()) {
      List<ParameterConfig> results = new ArrayList<>();
      List<Long> ids = DBUtils.getLongList(conn,
          String.format("SELECT PC.%s FROM %s PC WHERE PC.%s = '%s'",
              ParameterConfig.FIELD_ID, ParameterConfig.TABLE_NAME,
              ParameterConfig.FIELD_OWNER_ID, ownerId));
      for (Long id : ids) {
        ParameterConfig model = new ParameterConfig();
        model.setId(id);
        model.load(conn);
        results.add(model);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<BotStrategyConfig> getBotStrategies(String botId) {
    try (Connection conn = getConnection()) {
      List<BotStrategyConfig> results = new ArrayList<>();
      List<Long> ids = DBUtils.getLongList(conn,
          String.format("SELECT SC.%s FROM %s SC WHERE SC.%s = '%s'",
              BotStrategyConfig.FIELD_ID, BotStrategyConfig.TABLE_NAME,
              BotStrategyConfig.FIELD_BOT_ID, botId));
      for (Long id : ids) {
        BotStrategyConfig strategy = new BotStrategyConfig();
        strategy.setId(id);
        strategy.load(conn);
        results.add(strategy);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<BotStopStrategyConfig> getBotStopStrategies(String botId) {
    try (Connection conn = getConnection()) {
      List<BotStopStrategyConfig> results = new ArrayList<>();
      List<Long> ids = DBUtils.getLongList(conn,
          String.format("SELECT SSC.%s FROM %s SSC WHERE SSC.%s = '%s'",
              BotStopStrategyConfig.FIELD_ID, BotStopStrategyConfig.TABLE_NAME,
              BotStopStrategyConfig.FIELD_BOT_ID, botId));
      for (Long id : ids) {
        BotStopStrategyConfig strategy = new BotStopStrategyConfig();
        strategy.setId(id);
        strategy.load(conn);
        results.add(strategy);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<BotFilterConfig> getBotFilters(String botId) {
    try (Connection conn = getConnection()) {
      List<BotFilterConfig> results = new ArrayList<>();
      List<Long> ids = DBUtils.getLongList(conn,
          String.format("SELECT FC.%s FROM %s FC WHERE FC.%s = '%s'",
              BotFilterConfig.FIELD_ID, BotFilterConfig.TABLE_NAME,
              BotFilterConfig.FIELD_BOT_ID, botId));
      for (Long id : ids) {
        BotFilterConfig filter = new BotFilterConfig();
        filter.setId(id);
        filter.load(conn);
        results.add(filter);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<String> getBotTraders(String botId) {
    try (Connection conn = getConnection()) {
      return DBUtils.getStringList(conn,
          String.format("SELECT TC.%s FROM %s TC WHERE TC.%s = '%s'",
              TraderConfig.FIELD_ID, TraderConfig.TABLE_NAME,
              TraderConfig.FIELD_BOT_ID, botId));
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static SymbolInfo getSymbolInfo(Long symbolId) {
    try (Connection conn = getConnection()) {
      SymbolInfo symbol = new SymbolInfo();
      symbol.setSymbolId(symbolId);
      symbol.load(conn);
      return symbol;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static List<ServiceConfig> getServiceConfigs(boolean updatedOnly) {
    try (Connection conn = getConnection()) {
      List<ServiceConfig> results = new ArrayList<>();
      String sql = String.format("SELECT SC.%s FROM %s SC",
          ServiceConfig.FIELD_ID, ServiceConfig.TABLE_NAME);
      if (updatedOnly) {
        sql += " WHERE 1 = SC." + ServiceConfig.FIELD_UPDATED;
      }
      List<String> ids = DBUtils.getStringList(conn, sql);
      for (String id : ids) {
        ServiceConfig service = new ServiceConfig();
        service.setId(id);
        service.load(conn);
        results.add(service);
      }
      return results;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static boolean isUpdated(UserObject userObject) {
    try (Connection conn = getConnection()) {
      Integer updated = DBUtils.getIntValue(conn,
          String.format("SELECT O.%s FROM %s O " +
                  "WHERE O.%s = '%s'",
              UserObject.FIELD_UPDATED, userObject.getTableName(),
              UserObject.FIELD_ID, userObject.getId()));

      return Objects.equals(updated, 1);
    } catch (SQLException | ClassNotFoundException e) {
      LOGGER.error("Unsuccessful 'update' flag loading for USER_OBJECT " + userObject.getId(), e);
      return false;
    }
  }

  public static boolean resetUpdated(UserObject userObject) {
    try (Connection conn = getConnection()) {
      return DBUtils.executeQuery(conn,
          String.format("UPDATE %s O SET O.%s = %s" +
                  "WHERE O.%s = '%s'",
              userObject.getTableName(), UserObject.FIELD_UPDATED, 0,
              UserObject.FIELD_ID, userObject.getId()));
    } catch (SQLException | ClassNotFoundException e) {
      LOGGER.error("Unsuccessful 'update' flag resetting for USER_OBJECT " + userObject.getId(), e);
      return false;
    }
  }

  public static AppUser getAdminUserConfig() {
    return getUserConfig(1L);
  }

  public static AppUser getUserConfig(long id) {
    try (Connection conn = getConnection()) {
      AppUser user = new AppUser();
      user.setUserId(id);
      user.load(conn);
      return user;
    } catch (SQLException | ClassNotFoundException e) {
      LOGGER.error("Unsuccessful user config loading for id " + id, e);
      return null;
    }
  }

  public static boolean updateUserTelegramChatId(String tgUsername, String tgChatId) {
    try (Connection conn = getConnection()) {
      return DBUtils.executeQuery(conn,
          String.format("UPDATE %s AU SET AU.%s = '%s' WHERE AU.%s = '%s'",
              AppUser.TABLE_NAME,
              AppUser.FIELD_TG_CHAT_ID, tgChatId,
              AppUser.FIELD_TG_USER_NAME, tgUsername));
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static AppUser findUserWithName(String username) {
    try (Connection conn = getConnection()) {
      Long id = DBUtils.getLongValue(conn,
          String.format("SELECT AU.%s FROM %s AU WHERE AU.%s = '%s'",
              AppUser.FIELD_USER_ID,
              AppUser.TABLE_NAME,
              AppUser.FIELD_USER_NAME, username));
      if (id != null) {
        return getUserConfig(id);
      }
      return null;
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }

  public static boolean createTelegramBotUser(String tgUsername, String chatId) {
    AppUser user = findUserWithName(tgUsername);
    if (user != null && !chatId.equals(user.getTgChatId())) {
      //todo update chatId
      return false;
    } else if (user == null) {
      try (Connection conn = getConnection()) {
        Long newId = DBUtils.generateNext(conn, "seq_generator");
        AppUser newUser = new AppUser();
        newUser.setUserId(newId);
        newUser.setUserLogin(tgUsername);
        newUser.setUserName(tgUsername);
        newUser.setTgUserName(tgUsername);
        newUser.setTgChatId(chatId);
        newUser.setCreatedTs(new Timestamp(new Date().getTime()));
        newUser.save(conn);
        return true;
      } catch (SQLException | ClassNotFoundException e) {
        throw new SystemException(e);
      }
    }
    return false;
  }

  public static AppUser findOwnerUser(String objectId) {
    try (Connection conn = getConnection()) {
      TraderConfig obj = TraderConfig.createAndLoad(conn, objectId);
      return getUserConfig(obj.getCreatedBy());
    } catch (SQLException | ClassNotFoundException e) {
      throw new SystemException(e);
    }
  }
}