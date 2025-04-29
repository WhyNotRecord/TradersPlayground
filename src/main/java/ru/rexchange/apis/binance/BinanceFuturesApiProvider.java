package ru.rexchange.apis.binance;

import binance.futures.enums.IntervalType;
import binance.futures.enums.PositionMode;
import binance.futures.impl.UnsignedClient;
import binance.futures.model.AccountBalance;
import binance.futures.model.Candle;
import binance.futures.model.ExchangeInfo;
import binance.futures.model.ExchangeInfoEntry;
import com.google.gson.Gson;
import ru.rexchange.exception.SystemException;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.tools.FileUtils;
import ru.rexchange.tools.Utils;
import ru.rexchange.trading.trader.BinanceSignedClient;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BinanceFuturesApiProvider {
  public static final int REQUEST_ATTEMPTS_COUNT = 3;
  public static final long FAILED_REQUEST_REPEAT_PAUSE = 500L;
  public static final long EXCHANGE_INFO_CACHE_LIVE_TIME = 60 * 60 * 1000L;
  public static final long BALANCE_INFO_CACHE_LIVE_TIME = 15 * 1000L;
  //todo вынести отсюда в BinanceSignedClient всё, что требует авторизации

  public static boolean canTrade(BinanceSignedClient client) throws Exception {
      return client.canTrade();
  }

  public static boolean isHedgeModeEnabled(BinanceSignedClient client) throws Exception {
    return PositionMode.HEDGE.equals(client.getPositionMode());
  }

  private static final Map<String, BalanceInfo> balanceInfoCache = new HashMap<>();
  private static synchronized List<AccountBalance> getBalances(BinanceSignedClient client) throws Exception {
    if (!balanceInfoCache.containsKey(client.toString()) ||
        balanceInfoCache.get(client.toString()).getTimestamp() + BALANCE_INFO_CACHE_LIVE_TIME < DateUtils.currentTimeMillis()) {
      balanceInfoCache.put(client.toString(), new BalanceInfo(client.getBalance()));
    }
    return balanceInfoCache.get(client.toString()).getBalances();
  }

  /*public static BigDecimal getFreeAssetBalance(String currency, BinanceSignedClient client) throws Exception {
    List<AccountBalance> balances = getBalances(client);
    Optional<AccountBalance> asset =
          balances.stream().filter(asset1 -> currency.equals(asset1.getAsset())).findFirst();
    return asset.map(AccountBalance::getAvailableBalance).orElse(BigDecimal.ZERO);
  }*/

  public static AccountBalance getAssetBalance(String currency, BinanceSignedClient client) throws Exception {
    List<AccountBalance> balances = getBalances(client);
    Optional<AccountBalance> balance = balances.stream().filter(asset -> currency.equals(asset.getAsset())).findFirst();
    return balance.orElse(null);
  }

  private static ExchangeInfo exchangeInfoCache = null;
  public static ExchangeInfo getExchangeInfo() throws Exception {
    if (exchangeInfoCache == null ||
        exchangeInfoCache.getServerTime() + EXCHANGE_INFO_CACHE_LIVE_TIME < DateUtils.currentTimeMillis()) {
      try {
        exchangeInfoCache = UnsignedClient.getExchangeInformation();
        exchangeInfoCache.setServerTime(DateUtils.currentTimeMillis());//todo разобраться
        cacheOnDisk(exchangeInfoCache);
      } catch (Exception e) {
        exchangeInfoCache = loadFromDisk();
      }
    }
    return exchangeInfoCache;
  }

  private static void cacheOnDisk(ExchangeInfo exchangeInfo) {
    Gson gson = new Gson();
    String cont = gson.toJson(exchangeInfo, ExchangeInfo.class);
    new File(FileUtils.CACHE_DIR).mkdirs();
    FileUtils.writeStringToFileSafe(getDiskCacheFilename(), cont, false);
  }

  private static ExchangeInfo loadFromDisk() {
    Gson gson = new Gson();
    ExchangeInfo cont = gson.fromJson(FileUtils.readFileContent(getDiskCacheFilename()), ExchangeInfo.class);
    return cont;
  }

  private static String getDiskCacheFilename() {
    return String.format("%s/bin_exch_inf.dat", FileUtils.CACHE_DIR);
  }

  public static ExchangeInfoEntry getSymbolInfo(String pair) {
    try {
      Optional<ExchangeInfoEntry> symbolInfo = getExchangeInfo().getSymbols().
          stream().filter(exchangeInfoEntry -> pair.equals(exchangeInfoEntry.getSymbol())).findFirst();
      return symbolInfo.orElse(null);
    } catch (Exception e) {
      BinanceOrdersProcessor.LOGGER.warn("Error occurred while getting symbol information", e);
      return null;
    }
  }

  public static synchronized Candle getLastPrice(String symbol) {
    try {
      return Utils.executeInFewAttempts(() -> {
        List<Candle> klines = UnsignedClient.getKlines(symbol, IntervalType._1m, 1,
            DateUtils.currentTimeMillis() - 1000L * 60L);
        if (klines.isEmpty())
          throw new SystemException("Response for last price request for symbol %s is empty", symbol);
        return klines.get(0);
      }, REQUEST_ATTEMPTS_COUNT, FAILED_REQUEST_REPEAT_PAUSE);
    } catch (Exception e) {
      BinanceOrdersProcessor.LOGGER.warn("Error occurred while getting last price", e);
      return null;
    }
  }


  private static class BalanceInfo {
    private final List<AccountBalance> balances;
    private final long timestamp;
    public BalanceInfo(List<AccountBalance> balances) {
      this.balances = balances;
      this.timestamp = DateUtils.currentTimeMillis();
    }

    public List<AccountBalance> getBalances() {
      return balances;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
