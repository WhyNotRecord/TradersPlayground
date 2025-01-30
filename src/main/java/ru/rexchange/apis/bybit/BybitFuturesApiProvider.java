package ru.rexchange.apis.bybit;

import ru.rexchange.trading.trader.BybitSignedClient;

public class BybitFuturesApiProvider {
  public static boolean canTrade(BybitSignedClient client) throws Exception {
    return client.canTrade();
  }
}
