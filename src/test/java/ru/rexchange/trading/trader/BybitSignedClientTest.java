package ru.rexchange.trading.trader;

import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.PositionIdx;
import com.bybit.api.client.domain.trade.Side;
import org.junit.jupiter.api.Test;
import ru.rexchange.apis.bybit.BybitOrdersProcessorTest;
import ru.rexchange.exception.SystemException;
import ru.rexchange.test.TestTools;

import java.util.Map;

import static ru.rexchange.trading.trader.BybitSignedClient.FIELD_ORDER_ID;

public class BybitSignedClientTest {
  @Test
  public void testDealOpeningAndClosing() throws Exception {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = BybitOrdersProcessorTest.getSignedClient();
    String symbol = "ETHUSDT";
    Map<String, Object> order =
        apiClient.placeOrder(symbol, Side.BUY, PositionIdx.HEDGE_MODE_BUY,
            TradeOrderType.LIMIT, "0.01", "1500");
    String orderId = order.containsKey(FIELD_ORDER_ID) ? (String) order.get(FIELD_ORDER_ID) : null;
    if (orderId != null) {
      if (!apiClient.cancelOrder(symbol, orderId))
        throw new SystemException("Unsuccessful order cancelling! Cancel order %s manually", orderId);
    }
  }
}
