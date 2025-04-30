package ru.rexchange.trading.trader;

import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.PositionIdx;
import com.bybit.api.client.domain.trade.Side;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ru.rexchange.apis.bybit.BybitOrdersProcessorTest;
import ru.rexchange.exception.SystemException;
import ru.rexchange.test.TestTools;
import ru.rexchange.tools.StringUtils;
import ru.rexchange.trading.trader.futures.BybitOrder;

import java.util.Map;

import static ru.rexchange.trading.trader.BybitSignedClient.FIELD_ORDER_ID;

public class BybitSignedClientTest {
  @Test
  public void testBalanceCheck() throws Exception {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = BybitOrdersProcessorTest.getSignedClient();
    String symbol = "USDT";
    Float[] result = apiClient.getBalancesFloat(symbol);
    System.out.println(StringUtils.toString(result));
  }

  @Test
  @Disabled
  public void testDealOpeningAndClosing() throws Exception {
    TestTools.prepareEnvironment();
    BybitSignedClient apiClient = BybitOrdersProcessorTest.getSignedClient();
    String symbol = "ETHUSDT";
    BybitOrder order =
        apiClient.placeOrder(symbol, Side.BUY, PositionIdx.HEDGE_MODE_BUY,
            TradeOrderType.LIMIT, "0.01", "1500", null, null);
    //System.out.println(apiClient.getOrders(false, symbol));
    //todo check TP & SL
    String orderId = order.getString(FIELD_ORDER_ID);
    if (orderId != null) {
      if (!apiClient.cancelOrder(symbol, orderId))
        throw new SystemException("Unsuccessful order cancelling! Cancel order %s manually", orderId);
    }
  }
}
