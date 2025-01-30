package ru.rexchange.apis.kucoin;

import com.kucoin.futures.core.rest.response.TickerResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.rexchange.exception.SystemException;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.test.TestTools;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.KucoinSignedClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class KucoinOrdersProcessorTest {
  @Test
  public void testBitcoinOrderPreparing() throws IOException {
    TestTools.prepareEnvironment();
    KucoinSignedClient apiClient = getFakeApiClient();
    KucoinOrdersProcessor.PositionContainer result = (KucoinOrdersProcessor.PositionContainer)
        KucoinOrdersProcessor.getInstance(true).placeOrder(apiClient, 26122.155f,
        "BTCUSDT", 0.0049f, 5, true, 26385.615f, 26053.266f);
    Assertions.assertNotNull(result);
    Assertions.assertNotNull(result.getPositionInfo());
    Assertions.assertEquals(new BigDecimal("0.004"), getBD(result.getPositionInfo().getAmount().floatValue(), 3));
    //TODO сделать, чтобы зависело от сдвига в KucoinOrdersProcessor
    Assertions.assertEquals(new BigDecimal("26122.3"), getBD(result.getPositionInfo().getAveragePrice(), 1));
    Assertions.assertNotNull(result.getStopLossOrder());
    Assertions.assertEquals(new BigDecimal("26385.6"), getBD(result.getStopLossOrder().getPrice(), 1));
    Assertions.assertNotNull(result.getTakeProfitOrder());
    Assertions.assertEquals(new BigDecimal("26053.2"), getBD(result.getTakeProfitOrder().getPrice(), 1));
  }

  @NotNull
  private static KucoinSignedClient getFakeApiClient() {
    return new KucoinSignedClient(new TraderAuthenticator("Kucanchik", "", ""));
  }

  /*@NotNull
  private static KucoinSignedClient getRealApiClient() {
    for (ApiConfig rec : DatabaseInteractor.loadApiCredentials()) {
      if (rec.getApiName().contains("Kucoin"))
        return new KucoinSignedClient(new TraderAuthenticator(rec));
    }
    throw new UserException("Не удалось обнаружить ключей API для биржи Kucoin");
  }*/

  private BigDecimal getBD(Float value, int precision) {
    return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_EVEN);
  }

  @Test
  public void testPriceConversion() {
    String priceStr = new BigDecimal("190.1863500").
        setScale(new BigDecimal("1").scale(), RoundingMode.HALF_DOWN).toString();
    Assertions.assertFalse(priceStr.contains("E"));
  }

  @Test
  public void testAveragePriceEvaluation() {
    String pair = "BTCUSDT";
    KucoinOrdersProcessor p = KucoinOrdersProcessor.getInstance(true);

    AbstractPositionContainer pos1 = p.createPositionContainer(p.convertOrder(
        KucoinOrdersProcessor.createTestOrder(pair, new BigDecimal("40"), new BigDecimal("1"),
            KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.MARKET, null), null)
    );
    pos1.addOrder(p.convertOrder(
        KucoinOrdersProcessor.createTestOrder(pair, new BigDecimal("20"), new BigDecimal("0.8"),
            KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.LIMIT, null), pos1.getPositionInfo().getPositionId()));
    Assertions.assertEquals(pos1.getAvgPrice().setScale(6, RoundingMode.HALF_EVEN), new BigDecimal("0.933333"));

    AbstractPositionContainer pos2 = p.createPositionContainer(p.convertOrder(
        KucoinOrdersProcessor.createTestOrder(pair, new BigDecimal("40"), new BigDecimal("1"),
            KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.MARKET, null), null)
    );
    pos2.addOrder(p.convertOrder(
        KucoinOrdersProcessor.createTestOrder(pair, new BigDecimal("40"), new BigDecimal("0.8"),
            KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.LIMIT, null),
        pos2.getPositionInfo().getPositionId()));
    pos2.addOrder(p.convertOrder(
        KucoinOrdersProcessor.createTestOrder(pair, new BigDecimal("20"), new BigDecimal("0.4"),
            KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.LIMIT, null),
        pos2.getPositionInfo().getPositionId()));
    Assertions.assertEquals(pos2.getAvgPrice().setScale(6, RoundingMode.HALF_EVEN),
        new BigDecimal("0.800000"));
  }

  @Test
  public void testDealOpeningAndClosing() throws Exception {
    TestTools.prepareEnvironment();
    KucoinSignedClient apiClient = getFakeApiClient();
    String symbol = "ETHUSDT";
    TickerResponse priceData = KucoinFuturesApiProvider.getLastPrice(symbol);
    if (priceData == null)
      throw new SystemException("Last price request for symbol %s failed", symbol);
    float price = priceData.getPrice().floatValue();

    AbstractPositionContainer result = KucoinOrdersProcessor.getInstance(true).
        placeOrder(apiClient, null, true, price,
        symbol, 0.02f, 5, true, price * 0.97f, price * 1.04f);
    OrderInfoObject closeDeal = result.closeDeal(apiClient);
    Assertions.assertNotNull(closeDeal);
  }

  /*Не имеет смысла, поскольку Kucoin не поддерживает хеджирование во фьючерсах
  @Test
  public void testOppositePositionsOpening() throws Exception {
    TestTools.prepareEnvironment();
    String symbol = "LTCUSDT";
    boolean testOrders = true;
    KucoinSignedClient apiClient = testOrders ? getFakeApiClient() : getRealApiClient();

    AbstractPositionContainer buyPos = KucoinOrdersProcessor.getInstance(testOrders).
        placeMarketOrder(apiClient, symbol, 1.5f, 10, true);
    AbstractPositionContainer sellPos = KucoinOrdersProcessor.getInstance(testOrders).
        placeMarketOrder(apiClient, symbol, 1.5f, 10, false);
  }*/
}
