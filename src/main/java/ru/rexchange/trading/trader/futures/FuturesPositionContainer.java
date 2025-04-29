package ru.rexchange.trading.trader.futures;

import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.AbstractSignedClient;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public abstract class FuturesPositionContainer<C extends AbstractSignedClient> extends AbstractPositionContainer<C> {
  @Override
  public int update(C apiAccess) /*throws SocketException, UnknownHostException*/ {
    int result = 0;
    if (apiAccess == null)
      return result;
    LOGGER.trace("Updating position container with id = {}", position.getPositionId());

    AbstractOrdersProcessor<Object, C> processor = (AbstractOrdersProcessor<Object, C>) getOrdersProcessor();
    if (getStopLossOrder() != null) {
      LOGGER.trace("Updating stop loss order {}", getStopLossOrder().getOrderId());
      try {
        Object slUpdated = processor.updateOrder(apiAccess, getStopLossOrder());
        if (slUpdated != null) {
          stopLossOrder = processor.convertOrder(slUpdated, getPositionInfo().getPositionId());
          result++;
        }
      } catch (SocketException | UnknownHostException e) {
        LOGGER.warn("Failed to update stop loss order {}", getStopLossOrder().getOrderId(), e);
      }
    }
    if (getTakeProfitOrder() != null) {
      LOGGER.trace("Updating take-profit order {}", getTakeProfitOrder().getOrderId());
      try {
        Object tpUpdated = processor.updateOrder(apiAccess, getTakeProfitOrder());
        if (tpUpdated != null) {
          takeProfitOrder = processor.convertOrder(tpUpdated, getPositionInfo().getPositionId());
          result++;
        }
      } catch (SocketException | UnknownHostException e) {
        LOGGER.warn("Failed to update take-profit order {}", getTakeProfitOrder().getOrderId(), e);
      }
    }

    try {
      result += updateOrdersList(orders, processor, apiAccess);
      result += updateOrdersList(outdatedOrders, processor, apiAccess);
    } catch (SocketException | UnknownHostException e) {
      LOGGER.warn("Failed to update orders list", e);
    }
    updatePositionStatus();

    return result;
  }

  private int updateOrdersList(List<OrderInfoObject> orders, AbstractOrdersProcessor<Object, C> processor,
                               C apiAccess) throws SocketException, UnknownHostException {
    int result = 0;
    for (OrderInfoObject order : new ArrayList<>(orders)) {
      OrderInfoObject orderUpdated = processor.convertOrder(
          processor.updateOrder(apiAccess, order), getPositionInfo().getPositionId());
      if (orderUpdated != null) {
        orders.remove(order);
        orders.add(orderUpdated);
        result++;
      }
    }
    return result;
  }

}
