package ru.rexchange.apis.bybit;

import com.bybit.api.client.domain.position.PositionMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.test.TestTools;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.BybitSignedClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BybitOrdersProcessorTest {
  @Test
  public void testBitcoinOrderPreparing() throws IOException {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = getSignedClient();
    BybitOrdersProcessor.PositionContainer result = (BybitOrdersProcessor.PositionContainer)
        BybitOrdersProcessor.getInstance(true).placeOrder(apiClient, 26122.155f,
        "BTCUSDT", 0.0049f, 5, true, 26385.615f, 26053.266f);
    Assertions.assertNotNull(result);
    Assertions.assertNotNull(result.getLastOrder());
    Assertions.assertEquals(new BigDecimal("0.005"), getBD(result.getLastOrder().getAmount().floatValue(), 3));
    //TODO сделать, чтобы зависело от сдвига в BybitOrdersProcessor
    Assertions.assertEquals(new BigDecimal("26122.4"), getBD(result.getLastOrder().getPrice(), 1));
    Assertions.assertNotNull(result.getStopLossOrder());
    Assertions.assertEquals(new BigDecimal("26385.6"), getBD(result.getStopLossOrder().getPrice(), 1));
    Assertions.assertNotNull(result.getTakeProfitOrder());
    Assertions.assertEquals(new BigDecimal("26053.3"), getBD(result.getTakeProfitOrder().getPrice(), 1));
  }

  @Test
  public void testBasicQueries() throws Exception {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = getSignedClient();
    apiClient.getBalance("USDT");
    String symbol = "ETHUSDT";
    String btcSymbol = "BTCUSDT";
    if (apiClient.getPositionMode(symbol) != PositionMode.BOTH_SIDES)
      apiClient.setPositionMode(symbol, true);
    Assertions.assertEquals(apiClient.getPositionMode(symbol), PositionMode.BOTH_SIDES);
    Assertions.assertEquals(apiClient.getPositionMode(btcSymbol), PositionMode.MERGED_SINGLE);
  }


  @NotNull
  public static BybitSignedClient getSignedClient() {
    return new BybitSignedClient(new TraderAuthenticator("BybAuth",
        System.getProperty(TestTools.API_KEY_PROPERTY),
        System.getProperty(TestTools.API_SECRET_KEY_PROPERTY)));
  }

  private BigDecimal getBD(Float value, int precision) {
    return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_EVEN);
  }

  /*@Test
  public void testPriceConversion() {
    String priceStr = new BigDecimal("190.1863500").
        setScale(new BigDecimal("1").scale(), RoundingMode.HALF_DOWN).toString();
    Assertions.assertFalse(priceStr.contains("E"));
  }*/

  @Test
  public void testAveragePriceEvaluation() {
    String pair = "BTCUSDT";
    BybitOrdersProcessor p = BybitOrdersProcessor.getInstance(true);

    /*todo AbstractPositionContainer pos1 = p.createPositionContainer(p.convertOrder(
        BybitOrdersProcessor.createTestOrder(pair, "40", "1",
            OrderSide.BUY, PositionSide.LONG, OrderType.MARKET), null)
    );
    pos1.addOrder(p.convertOrder(BybitOrdersProcessor.createTestOrder(pair, "20", "0.8",
        OrderSide.BUY, PositionSide.LONG, OrderType.LIMIT), pos1.getPositionInfo().getPositionId()));
    Assertions.assertEquals(pos1.getAvgPrice().setScale(6, RoundingMode.HALF_EVEN), new BigDecimal("0.933333"));

    AbstractPositionContainer pos2 = p.createPositionContainer(p.convertOrder(
        BybitOrdersProcessor.createTestOrder(pair, "40", "1",
            OrderSide.BUY, PositionSide.LONG, OrderType.MARKET), null)
    );
    pos2.addOrder(p.convertOrder(BybitOrdersProcessor.createTestOrder(pair, "40", "0.8",
        OrderSide.BUY, PositionSide.LONG, OrderType.LIMIT), pos2.getPositionInfo().getPositionId()));
    pos2.addOrder(p.convertOrder(BybitOrdersProcessor.createTestOrder(pair, "20", "0.4",
        OrderSide.BUY, PositionSide.LONG, OrderType.LIMIT), pos2.getPositionInfo().getPositionId()));
    Assertions.assertEquals(pos2.getAvgPrice().setScale(6, RoundingMode.HALF_EVEN), new BigDecimal("0.800000"));*/
  }

  @Test
  public void testDealOpeningAndClosing() throws Exception {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = getSignedClient();
    String symbol = "ETHUSDT";
    Float price = BybitFuturesApiProvider.getLastPrice(symbol);
    Assertions.assertNotNull(price);
    BybitOrdersProcessor.PositionContainer result =
        BybitOrdersProcessor.getInstance(true).placeOrder(apiClient, null, false, price,
        symbol, 0.02f, 5, true, price * 0.97f, price * 1.04f);
    Assertions.assertNotNull(result);
    OrderInfoObject closeDeal = result.closeDeal(apiClient);
    Assertions.assertNotNull(closeDeal);
  }
}
