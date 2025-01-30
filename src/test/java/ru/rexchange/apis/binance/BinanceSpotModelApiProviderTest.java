package ru.rexchange.apis.binance;

import org.junit.jupiter.api.Test;
import ru.rexchange.tools.TimeUtils;

public class BinanceSpotModelApiProviderTest {
  @Test
  public void testLastCandleRequest() {
    BinanceModelApiProvider.CandleResponse result1m =
        BinanceModelApiProvider.getLastCandle(new String[] {"ETH", "USDT"}, TimeUtils.getPeriodSymbol(60));
    BinanceModelApiProvider.CandleResponse result15m =
        BinanceModelApiProvider.getLastCandle(new String[] {"ETH", "USDT"}, TimeUtils.getPeriodSymbol(900));
  }
}
