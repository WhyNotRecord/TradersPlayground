package ru.rexchange.apis.bybit;

import ru.rexchange.trading.trader.BybitSignedClient;

public class BybitFuturesApiProvider {
  public static boolean canTrade(BybitSignedClient client) throws Exception {
    return client.canTrade();
  }

  public static Object getLastPrice(String symbol) {
    return null;//todo implement method that makes request to bybit API for getting last price
  }
}
