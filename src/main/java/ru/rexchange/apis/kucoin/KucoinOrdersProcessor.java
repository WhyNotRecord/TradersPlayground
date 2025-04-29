package ru.rexchange.apis.kucoin;

import com.kucoin.futures.core.rest.response.ContractResponse;
import com.kucoin.futures.core.rest.response.OrderResponse;
import com.kucoin.futures.core.rest.response.TickerResponse;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.Consts;
import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.PositionInfoObject;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.tools.Utils;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.KucoinSignedClient;
import ru.rexchange.trading.trader.futures.FuturesPositionContainer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class KucoinOrdersProcessor extends AbstractOrdersProcessor<OrderResponse, KucoinSignedClient> {
  public static final long NANOSECONDS_DIVISOR = 1000000L;
  protected static Logger LOGGER = LoggerFactory.getLogger(KucoinOrdersProcessor.class);
  private static KucoinOrdersProcessor instance = null;
  private static final long OPEN_ORDERS_CACHE_LIVE_TIME = 60 * 1000L;
  @Setter
  private boolean testOrders = false;
  /*public static final List<String> NOT_EXECUTED_ORDER_STATUSES = Arrays.asList(OrderInfoObject.OrderStatus.NEW,
      "OrderInfoObject.OrderStatus.CANCELED", "OrderInfoObject.OrderStatus.EXPIRED");*/

  protected KucoinOrdersProcessor(boolean testOrders) {
    this.testOrders = testOrders;
  }

  public static KucoinOrdersProcessor getInstance(boolean testOrders) {
    if (instance == null) {
      instance = new KucoinOrdersProcessor(testOrders);
    }
    return instance;
  }

  @Override
  public OrderInfoObject limitOrder(KucoinSignedClient aClient, float price, String pair,
                                    double amount, boolean buy, Integer leverage) throws UserException {
    LOGGER.debug("Placing a {} limit order for {}", buy ? "buy" : "sell", pair);
    checkMarginMode(aClient, pair);

    try {
      ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfo(pair);
      BigDecimal preparedAmount = checkAndFitAmount(symbolInfo, price, amount);
      BigDecimal preparedPrice = checkAndFitPrice(symbolInfo, price, 0);

      OrderResponse order = buy ?
          openBuy(aClient, true, symbolInfo.getSymbol(), preparedAmount, preparedPrice, leverage) :
          openSell(aClient, true, symbolInfo.getSymbol(), preparedAmount, preparedPrice, leverage);
      LOGGER.debug("Posted a limit {} order with id {} for {} {} by {}", order.getSide(), order.getClientOid(), preparedAmount, pair, preparedPrice);
      return convertOrder(order, null);
    } catch (Exception e) {
      LOGGER.error("Place {} order failed: {} {}", (buy ? "buy" : "sell"), amount, pair);
      throw new SystemException(e);
    }
  }

  @Override
  public PositionContainer createEmptyPositionContainer() {
    return new PositionContainer();
  }

  private String unwrapSymbol(String kucoinSymbol) {
    String result = kucoinSymbol.replace("XBT", "BTC");
    if (result.endsWith("M"))
      return result.substring(0, result.length() - 1);
    return result;
  }

  @Override
  public OrderInfoObject queryOrder(KucoinSignedClient client, String symbol, String clientOrderId) throws UserException {
    try {
      return convertOrder(client.queryOrder(symbol, null, clientOrderId), null);
    } catch (Exception e) {
      throw new UserException("Error occurred while querying order " + clientOrderId, e);
    }
  }

  @Override
  public OrderInfoObject convertOrder(OrderResponse customOrder, String positionId) {
    if (customOrder == null)
      return null;
    OrderInfoObject result = new OrderInfoObject();
    fillOrderObject(result, customOrder, positionId);
    return result;
  }

  private OrderInfoObject fillOrderObject(OrderInfoObject dstOrder, OrderResponse srcOrder, String parentId) {
    String localSymbol = unwrapSymbol(srcOrder.getSymbol());
    ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfoSafe(localSymbol);
    dstOrder.setDirection(srcOrder.getSide().toUpperCase());
    dstOrder.setSymbol(localSymbol);
    dstOrder.setPrice(srcOrder.getPrice().floatValue());
    if (dstOrder.getPrice() == 0 && srcOrder.getStopPrice() != null)
      dstOrder.setPrice(srcOrder.getStopPrice().floatValue());
    dstOrder.setStatus(evaluateStatus(srcOrder));
    //TODO привести типы ордеров к единому виду и не забыть про updateOrder в BackTestingOrdersProcessor
    dstOrder.setType(defineType(srcOrder));
    if (dstOrder.getType() == null)
      dstOrder.setType(srcOrder.getType());
    BigDecimal amount = unwrapKucoinAmount(symbolInfo, srcOrder.getSize());
    dstOrder.setAmount(amount.doubleValue());
    BigDecimal execAmount = unwrapKucoinAmount(symbolInfo, srcOrder.getDealSize());
    dstOrder.setExecutedAmount(execAmount);
    //dstOrder.setAvgPrice(srcOrder.getDealValue());
    dstOrder.setPositionId(parentId);
    dstOrder.setOrderTimestamp(new Timestamp(srcOrder.getOrderTime() / NANOSECONDS_DIVISOR));
    dstOrder.setOrderId(srcOrder.getClientOid());
    dstOrder.setExternalId(srcOrder.getId());

    return dstOrder;
  }

  private String defineType(OrderResponse order) {
    //todo пока считаем, что базовому рыночному ордеру всё равно дальше проставится нужный тип
    if (KucoinSignedClient.OrderType.MARKET.equals(order.getType()))
      return OrderInfoObject.Type.CLOSE_ORDER;
    //&& getClosingOrderSide(buy), getClosingPositionSide(buy),
    return null;
  }

  private static BigDecimal unwrapKucoinAmount(ContractResponse symbolInfo, BigDecimal amount) {
    if (amount == null)
      return null;
    if (symbolInfo != null) {
      BigDecimal lot = BigDecimal.valueOf(symbolInfo.getLotSize() * symbolInfo.getMultiplier()).round(MathContext.DECIMAL32);
      return amount.multiply(lot);
    } else {
      LOGGER.warn("Symbol data is not provided. Order size could be incorrect");
      return amount;
    }
  }

  private String evaluateStatus(OrderResponse srcOrder) {
    if (KucoinSignedClient.OrderStatus.OPEN.equals(srcOrder.getStatus()))
      return OrderInfoObject.OrderStatus.NEW;
    else if (KucoinSignedClient.OrderStatus.DONE.equals(srcOrder.getStatus())) {
      if (orderIsFilled(srcOrder)) {
        return OrderInfoObject.OrderStatus.FILLED;
      }
      else if (srcOrder.isCancelExist())
        return OrderInfoObject.OrderStatus.CANCELED;
    }
    LOGGER.trace("evaluateStatus: default");
    return OrderInfoObject.OrderStatus.NEW;
  }

  @Override
  public PositionContainer placeOrder(KucoinSignedClient aClient, AbstractPositionContainer<KucoinSignedClient> openPosition, boolean limit,
                                                             float price, String pair, double amount, Integer leverage, boolean buy,
                                                             @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    LOGGER.debug("Placing a {} {} order for {}", buy ? "buy" : "sell", limit ? "limit" : "market", pair);
    if (leverage != null && !testOrders) {
      setLeverageSafe(aClient, pair, leverage);
    }
    checkMarginMode(aClient, pair);
    try {
      ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfo(pair);
      BigDecimal preparedAmount = checkAndFitAmount(symbolInfo, price, amount);
      BigDecimal preparedPrice = checkAndFitPrice(symbolInfo, price,
          buy ? getLimitOrderShiftInTicks() : -getLimitOrderShiftInTicks());

      OrderResponse order = buy ?
          openBuy(aClient, limit, symbolInfo.getSymbol(), preparedAmount, preparedPrice, leverage) :
          openSell(aClient, limit, symbolInfo.getSymbol(), preparedAmount, preparedPrice, leverage);
      LOGGER.debug("Placed a {} order with id {} for {} {} by {}",
          order.getSide(), order.getClientOid(), preparedAmount, pair, preparedPrice);
      if (!KucoinSignedClient.OrderStatus.DONE.equals(order.getStatus())) {
        LOGGER.info("Order is {}, not {}!", order.getStatus(), KucoinSignedClient.OrderStatus.DONE);
      }
      PositionContainer result;

      if (openPosition == null) {
        result = (PositionContainer) (createPositionContainer(convertOrder(order, null)));
      } else {
        result = (PositionContainer) openPosition;
        openPosition.addOrder(convertOrder(order, openPosition.getPositionInfo().getPositionId()));
      }
      OrderResponse takeProfitOrder, stopLossOrder = null;
      List<OrderResponse> orders = getOpenOrders(aClient, pair);
      if (stopLoss != null && !Float.isNaN(stopLoss)) {
        stopLossOrder = placeStopLoss(aClient, orders, symbolInfo, result, stopLoss);
        if (stopLossOrder == null && cancelOrder(aClient, order)) {//todo доработать условия
          return null;
        }
        result.setStopLossOrder(convertOrder(stopLossOrder, result.getPositionInfo().getPositionId()));
      }
      if (takeProfit != null && !Float.isNaN(takeProfit)) {
        takeProfitOrder = placeTakeProfit(aClient, orders, symbolInfo, result, takeProfit);
        if (takeProfitOrder == null && cancelOrder(aClient, order)) {//todo доработать условия
          cancelOrder(aClient, stopLossOrder);
          return null;
        }
        result.setTakeProfitOrder(convertOrder(takeProfitOrder, result.getPositionInfo().getPositionId()));
      }

      return result;
    } catch (Exception e) {
      LOGGER.error("Place {} order failed: {} {}", (buy ? "buy" : "sell"), amount, pair);
      throw new SystemException(e);
    }
  }

  @Override
  public AbstractPositionContainer<KucoinSignedClient> placeMarketOrder(KucoinSignedClient aClient, AbstractPositionContainer<KucoinSignedClient> openPosition,
                                                                         String pair, double amount, Integer leverage,
                                                                         boolean buy) throws UserException {
    LOGGER.debug("Placing a {} market order for {}", buy ? "buy" : "sell", pair);
    if (leverage != null && !testOrders) {
      setLeverageSafe(aClient, pair, leverage);
    }
    checkMarginMode(aClient, pair);

    try {
      ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfo(pair);
      float lastPrice = getLastPrice(pair);
      BigDecimal convertedAmount = checkAndFitAmount(symbolInfo, lastPrice, amount);
      BigDecimal preparedPrice = checkAndFitPrice(symbolInfo, lastPrice, 0);

      OrderResponse order = buy ?
          openBuy(aClient, false, symbolInfo.getSymbol(), convertedAmount, preparedPrice, leverage) :
          openSell(aClient, false, symbolInfo.getSymbol(), convertedAmount, preparedPrice, leverage);
      LOGGER.debug("Placed a {} order with id {} for {} {} by {}",
          order.getSide(), order.getClientOid(), convertedAmount, pair, lastPrice);
      if (!KucoinSignedClient.OrderStatus.DONE.equals(order.getStatus())) {
        LOGGER.info("Order is {}, not {}!", order.getStatus(), KucoinSignedClient.OrderStatus.DONE);
      }
      OrderInfoObject response = convertOrder(order, null);
      if (openPosition == null)
        return createPositionContainer(response);
      else {
        openPosition.addOrder(response);
        return openPosition;
      }
    } catch (Exception e) {
      LOGGER.error("Place {} order on {} failed", (buy ? "buy" : "sell"), pair);
      throw new SystemException(e);
    }
  }

  private void checkMarginMode(KucoinSignedClient client, String symbol) {
    if (testOrders)
      return;
    try {
      client.switchMarginMode(symbol);
    } catch (IOException e) {
      LOGGER.error("Margin mode switching failed", e);
    }
  }

  /**
   * Round amount to base precision and lot size
   *
   * @param amount - amount to fit
   * @return fitted amount
   */
  public BigDecimal checkAndFitAmount(ContractResponse symbolInfo, float price, double amount) throws UserException {
    BigDecimal result = BigDecimal.valueOf(amount).round(MathContext.DECIMAL32);

    if (symbolInfo != null) {
      BigDecimal divisor = BigDecimal.valueOf(symbolInfo.getLotSize() * symbolInfo.getMultiplier()).round(MathContext.DECIMAL32);;
      BigDecimal[] division = result.divideAndRemainder(divisor);
      result = division[0];
      if (BigDecimal.ZERO.compareTo(division[1]) != 0) {
        LOGGER.trace("Given amount {} is not dividable by {}, will be fitted", result, divisor);
        if (BigDecimal.ZERO.compareTo(result) == 0)
          throw new UserException("Amount is too small - %s. Min quantity = %s", result, divisor);
      }

      if (result.longValue() > symbolInfo.getMaxOrderQty())
        throw new UserException("Amount is too big - %s. Max quantity = %s", result, symbolInfo.getMaxOrderQty());
    }
    LOGGER.trace("Fitted amount is {}", result);
    return result;
  }

  /**
   * Round price to tick size
   *
   * @param price - price to fit
   * @param shift - shift (in ticks) to be applied to final price
   * @return fitted price
   */
  public BigDecimal checkAndFitPrice(ContractResponse symbolInfo, float price, int shift) {
    BigDecimal result = BigDecimal.valueOf(price).round(MathContext.DECIMAL32);

    if (symbolInfo != null) {
      BigDecimal divisor = BigDecimal.valueOf(symbolInfo.getTickSize()).round(MathContext.DECIMAL32);
      BigDecimal[] division = result.divideAndRemainder(divisor);
      if (BigDecimal.ZERO.compareTo(division[1]) != 0) {
        LOGGER.debug("Given price {} is not dividable by {}, will be fitted", result, divisor);
        result = division[0].multiply(divisor, MathContext.DECIMAL32);
        if (BigDecimal.ZERO.compareTo(result) == 0)
          throw new UserException("Price is too small - %s. Min quantity = %s", result, divisor);
      }
      if (shift != 0)
        result = result.add(divisor.multiply(BigDecimal.valueOf(shift)));

      if (result.compareTo(symbolInfo.getMaxPrice()) > 0)
        throw new UserException("Price is too big - %s. Max price = %s", result, symbolInfo.getMaxPrice());
    }
    LOGGER.trace("Fitted price value is {}", result);
    return result;
  }

  public static String setLeverage(KucoinSignedClient apiClient, String pair, Integer leverage) throws Exception {
    LOGGER.debug("Setting leverage for pair {} to {}", pair, leverage);
    return apiClient.setLeverage(pair, leverage) ? "success" : "failure";
  }

  public static String setLeverageSafe(KucoinSignedClient apiClient, String pair, Integer leverage) {
    try {
      return setLeverage(apiClient, pair, leverage);
    } catch (Exception e) {
      LOGGER.error("Unsuccessful leverage change for pair {}", pair, e);
      return null;
    }
  }


  private OrderResponse openBuy(KucoinSignedClient client, boolean limit, String pair, BigDecimal quantity,
                                BigDecimal price, Integer leverage) throws Exception {
    if (testOrders) {
      return createTestOrder(pair, quantity, price, KucoinSignedClient.OrderSide.BUY,
          limit ? KucoinSignedClient.OrderType.LIMIT : KucoinSignedClient.OrderType.MARKET, null,
          /*limit ? KucoinSignedClient.OrderStatus.OPEN :*/ KucoinSignedClient.OrderStatus.DONE);
    } else if (limit) {
      return client.postOrder(pair, KucoinSignedClient.OrderSide.BUY, KucoinSignedClient.OrderType.LIMIT,
          null, "GTC", quantity, price, leverage);
    } else {
      OrderResponse response = client.postOrder(pair, KucoinSignedClient.OrderSide.BUY,
          KucoinSignedClient.OrderType.MARKET, null, null, quantity, null, leverage);
      LOGGER.trace("Buy order: {}", response);
      //todo можно запрашивать данные о позициях и уточнять цену открытия
      if (response.getPrice() == null || BigDecimal.ZERO.compareTo(response.getPrice()) == 0)
        response.setPrice(price);
      return response;
    }
  }

  private OrderResponse openSell(KucoinSignedClient client, boolean limit, String pair, BigDecimal quantity,
                                 BigDecimal price, Integer leverage) throws Exception {
    if (testOrders) {
      return createTestOrder(pair, quantity, price, KucoinSignedClient.OrderSide.SELL,
          limit ? KucoinSignedClient.OrderType.LIMIT : KucoinSignedClient.OrderType.MARKET, null,
          /*limit ? KucoinSignedClient.OrderStatus.OPEN :*/ KucoinSignedClient.OrderStatus.DONE);
    } else if (limit) {
      return client.postOrder(pair, KucoinSignedClient.OrderSide.SELL, KucoinSignedClient.OrderType.LIMIT,
          null, "GTC", quantity, price, leverage);
    } else {
      OrderResponse response = client.postOrder(pair, KucoinSignedClient.OrderSide.SELL, KucoinSignedClient.OrderType.MARKET,
          null, null, quantity, null, leverage);
      LOGGER.trace("Sell order: {}", response);
      //todo можно запрашивать данные о позициях и уточнять цену открытия
      if (response.getPrice() == null || BigDecimal.ZERO.compareTo(response.getPrice()) == 0)
        response.setPrice(price);
      return response;
    }
  }

  protected OrderResponse closeDeal(KucoinSignedClient client, String pair, BigDecimal quantity, boolean buy) throws Exception {
    ContractResponse symbolInfo = KucoinFuturesApiProvider.getSymbolInfo(pair);
    float lastPrice = getLastPrice(pair);
    BigDecimal preparedAmount = checkAndFitAmount(symbolInfo, lastPrice, quantity.doubleValue());
    BigDecimal preparedPrice = checkAndFitPrice(symbolInfo, lastPrice, 0);
    if (testOrders) {
      return createTestOrder(pair, preparedAmount, preparedPrice, getClosingOrderSide(buy),
          KucoinSignedClient.OrderType.MARKET, null, KucoinSignedClient.OrderStatus.DONE);
    } else {
      OrderResponse response = client.postOrder(symbolInfo.getSymbol(), getClosingOrderSide(buy),
          KucoinSignedClient.OrderType.MARKET, null, true, null, preparedAmount, null, null);
      LOGGER.trace("Close order: {}", response);
      response.setPrice(preparedPrice);
      return response;
    }
  }

  protected OrderResponse placeTakeProfit(KucoinSignedClient apiClient, List<OrderResponse> orders,
                                          ContractResponse symbolInfo, PositionContainer position, float takeProfitValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = KucoinSignedClient.OrderSide.BUY.equalsIgnoreCase(positionInfo.getDirection());
    BigDecimal preparedPrice = BigDecimal.valueOf(takeProfitValue);
    //TODO возможность установки трейлинг стопа (percentage должен зависеть от riskLevel)
    try {
      //Отменяем существующий take-profit при наличии
      if (checkForExistingTakeProfit(apiClient, orders, buy, positionInfo.getSymbol()) != null) {//todo unknown order sent
        LOGGER.debug("Trying to recreate TP order at new price {}", takeProfitValue);
      }
      if (takeProfitValue == 0) {
        LOGGER.info("TP price equals zero, leaving position without take-profit");
        return null;
      }
      preparedPrice = checkAndFitPrice(symbolInfo, takeProfitValue, 0);
      BigDecimal preparedAmount = checkAndFitAmount(symbolInfo, takeProfitValue, positionInfo.getAmount());
      OrderResponse order;
      if (testOrders) {
        order = createTestOrder(positionInfo.getSymbol(), preparedAmount, preparedPrice,
            getClosingOrderSide(buy), KucoinSignedClient.OrderType.MARKET,
            getClosingStop(buy, OrderInfoObject.Type.TAKE_PROFIT), KucoinSignedClient.OrderStatus.OPEN);
      } else {
        //todo An open stop ... in the direction is existing
        order = apiClient.postOrder(symbolInfo.getSymbol(), getClosingOrderSide(buy),
            KucoinSignedClient.OrderType.MARKET, getClosingStop(buy, OrderInfoObject.Type.TAKE_PROFIT), null,
            preparedAmount, preparedPrice, null);
        LOGGER.trace("TP order: {}", order);
        if (order.getPrice() == null || BigDecimal.ZERO.compareTo(order.getPrice()) == 0)
          order.setPrice(preparedPrice);
      }
      LOGGER.debug("Set a {} take profit order with id {} for position {}",
          order.getSide(), order.getClientOid(), positionInfo.getPositionId());
      return order;
    } catch (Exception e) {
      //TODO e.getMessage().contains("is existing") - значит уже существует TP для позиции
      LOGGER.error("Failed {} TP order for {} {} by {}",
          positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), preparedPrice, e);
      return null;
    }
  }

  private Float checkForExistingTakeProfit(KucoinSignedClient apiClient, List<OrderResponse> openOrders,
                                           boolean buy, String symbol) {
    OrderResponse existingTakeProfit = findExistingStopOrder(openOrders,
        KucoinFuturesApiProvider.convertSymbolFormat(symbol),
        getClosingOrderSide(buy), getClosingStop(buy, OrderInfoObject.Type.TAKE_PROFIT),
        KucoinSignedClient.OrderType.MARKET);
    if (existingTakeProfit != null) {
      LOGGER.debug("Existing TP at {} found", existingTakeProfit.getStopPrice().floatValue());
      if (!cancelOrder(apiClient, existingTakeProfit))
        throw new SystemException("Unsuccessful TP order cancellation " + existingTakeProfit.getClientOid());
      return existingTakeProfit.getPrice().floatValue();
    }
    return null;
  }

  protected OrderResponse placeStopLoss(KucoinSignedClient apiClient, List<OrderResponse> orders,
                                        ContractResponse symbolInfo, PositionContainer position, float stopLossValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = KucoinSignedClient.OrderSide.BUY.equalsIgnoreCase(positionInfo.getDirection());
    BigDecimal preparedPrice = BigDecimal.valueOf(stopLossValue);
    try {
      //Отменяем существующий stop-loss при наличии
      if (checkForExistingStopLoss(apiClient, orders, buy, positionInfo.getSymbol()) != null) {//todo unknown order sent
        LOGGER.debug("Trying to recreate SL order at new price {}", stopLossValue);
      }
      preparedPrice = checkAndFitPrice(symbolInfo, stopLossValue, 0);
      BigDecimal preparedAmount = checkAndFitAmount(symbolInfo, stopLossValue, positionInfo.getAmount());
      OrderResponse order;
      if (testOrders) {
        order = createTestOrder(positionInfo.getSymbol(), preparedAmount, preparedPrice,
            getClosingOrderSide(buy), KucoinSignedClient.OrderType.MARKET,
            getClosingStop(buy, OrderInfoObject.Type.STOP_LOSS), KucoinSignedClient.OrderStatus.OPEN);
      } else {
        //todo An open stop ... in the direction is existing
        order = apiClient.postOrder(symbolInfo.getSymbol(), getClosingOrderSide(buy),
            KucoinSignedClient.OrderType.MARKET, getClosingStop(buy, OrderInfoObject.Type.STOP_LOSS), null,
            //TODO может измениться количество из-за конвертаций ордеров
            preparedAmount, preparedPrice, null);
        LOGGER.trace("SL order: {}", order);
        if (order.getPrice() == null || BigDecimal.ZERO.compareTo(order.getPrice()) == 0)
          order.setPrice(preparedPrice);
      }
      LOGGER.debug("Set a {} stop loss order with id {} for base order {}",
          order.getSide(), order.getClientOid(), positionInfo.getPositionId());
      return order;
    } catch (Exception e) {
      //TODO e.getMessage().contains("is existing") - значит уже существует SL для позиции
      LOGGER.error("Failed {} SL order for {} {} by {}",
          positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), preparedPrice, e);
      return null;
    }
  }

  private Float checkForExistingStopLoss(KucoinSignedClient apiClient, List<OrderResponse> openOrders,
                                         boolean buy, String symbol) {
    OrderResponse existingStopLoss = findExistingStopOrder(openOrders,
        KucoinFuturesApiProvider.convertSymbolFormat(symbol),
        getClosingOrderSide(buy), getClosingStop(buy, OrderInfoObject.Type.STOP_LOSS),
        KucoinSignedClient.OrderType.MARKET);
    if (existingStopLoss != null) {
      LOGGER.debug("Existing SL at {} found", existingStopLoss.getStopPrice().floatValue());
      if (!cancelOrder(apiClient, existingStopLoss))
        throw new SystemException("Unsuccessful SL order cancellation " + existingStopLoss.getClientOid());
      return existingStopLoss.getPrice().floatValue();
    }
    return null;
  }

  @Override
  public float getLastPrice(String symbol) throws Exception {
    TickerResponse priceData = Utils.getCachedObject("kucoin.price." + symbol, 3000L,
        () -> KucoinFuturesApiProvider.getLastPrice(symbol));
    if (priceData == null)
      throw new SystemException("Last price request for symbol %s failed", symbol);
    return priceData.getPrice().floatValue();
  }

  protected boolean cancelOrder(KucoinSignedClient apiClient, OrderResponse order) {
    if (order == null) {
      LOGGER.warn("Attempt to close non-existing order");
      return false;
    }
    if (orderIsFilled(order)) {
    //if (!KucoinSignedClient.OrderStatus.OPEN.equals(order.getStatus())) {//TODO а что делать в противном случае?
      LOGGER.warn("Order {} is {}, can not be canceled!", order.getClientOid(), order.getStatus());
      return order.isCancelExist();//todo check
    }
    LOGGER.debug("Cancelling {} order {} for {} by {} on {}",
        order.getSide(), order.getClientOid(), order.getSize(), order.getPrice(), order.getSymbol());
    if (testOrders) {
      LOGGER.debug("Test order on {} cancelled: amount - {} price - {}", order.getSymbol(), order.getSize(), order.getPrice());
      order.setStatus(OrderInfoObject.OrderStatus.CANCELED);
      return true;
    }
    try {
      return Utils.executeInFewAttempts(() ->
              apiClient.cancelOrder(order.getSymbol(), order.getId(), order.getClientOid()),
          3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error("Failed to cancel {} order {} for {} by {} on {}",
          order.getSide(), order.getClientOid(), order.getSize(), order.getPrice(), order.getSymbol(), e);
      return false;
    }
  }

  private static boolean orderIsFilled(OrderResponse order) {
    LOGGER.trace("Is filled? {}", order.toString());
    return (order.getStop() == null && KucoinSignedClient.OrderStatus.DONE.equals(order.getStatus())) ||
        Boolean.TRUE.equals(order.getStopTriggered()) || order.getFilledSize().compareTo(BigDecimal.ZERO) > 0;
  }

  /*public boolean cancelOrder(KucoinSignedClient apiClient, OrderInfoObject order) {
    return cancelOrder((KucoinSignedClient) apiClient, order);
  }*/

  public boolean cancelOrder(KucoinSignedClient apiClient, OrderInfoObject order) {
    if (order == null) {
      LOGGER.warn("Attempt to close non-existing order");
      return false;
    }
    if (!OrderInfoObject.OrderStatus.NEW.equals(order.getStatus())) {//TODO а что делать в противном случае?
      LOGGER.warn("Order {} is {}, can not be canceled!", order.getOrderId(), order.getStatus());
      return OrderInfoObject.OrderStatus.CANCELED.equals(order.getStatus());
    }
    LOGGER.debug("Cancelling {} order {} for {} by {} on {}",
        order.getDirection(), order.getOrderId(), order.getAmount(), order.getPrice(), order.getSymbol());
    if (testOrders) {
      LOGGER.debug("Test order on {} cancelled: amount - {} price - {}",
          order.getSymbol(), order.getAmount(), order.getPrice());
      order.setStatus(OrderInfoObject.OrderStatus.CANCELED);
      return true;
    }
    try {
      return Utils.executeInFewAttempts(() ->
              apiClient.cancelOrder(order.getSymbol(), order.getExternalId(), order.getOrderId()),
          3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error("Failed to cancel {} order {} for {} by {} on {}",
          order.getDirection(), order.getOrderId(), order.getAmount(), order.getPrice(), order.getSymbol(), e);
      return false;
    }
  }


  private static OrderResponse findExistingStopOrder(List<OrderResponse> orders, String symbol,
                                                     String orderSide, String stopType, String execType) {
    if (orders == null)
      return null;
    for (OrderResponse order : orders) {
      if (symbol.equals(order.getSymbol()) && orderSide.equalsIgnoreCase(order.getSide()) &&
          stopType.equals(order.getStop()) && execType.equals(order.getType()) && order.isCloseOrder())
        return order;
    }
    return null;
  }

  public OrderResponse updateOrder(KucoinSignedClient apiClient, OrderInfoObject order) throws UnknownHostException {
    if (order == null) {
      LOGGER.error("Trying to update null order");
      return null;
    }
    try {
      OrderResponse orderResponse = Utils.executeInFewAttempts(() ->
              apiClient.queryOrder(order.getSymbol(), null, order.getOrderId()),
          3, getDefaultAttemptPause());
      //этот долбаный Kucoin не выдаёт цену для рыночных ордеров
      if (orderResponse.getPrice() == null || BigDecimal.ZERO.equals(orderResponse.getPrice())) {
        orderResponse.setPrice(BigDecimal.valueOf(order.getPrice()));
      }
      return orderResponse;
    } catch (UnknownHostException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("Update order {} failed", order.getOrderId());
      throw new SystemException(e);
    }
  }

  private static long getDefaultAttemptPause() {
    return 1000L;
  }

  public static OrderResponse createTestOrder(String pair, BigDecimal quantity, BigDecimal price, String orderSide,
                                              String execType, String stopType) {
    return createTestOrder(pair, quantity, price, orderSide, execType, stopType, KucoinSignedClient.OrderStatus.DONE);
  }

  /*@NotNull
  public static OrderResponse createTestOrder(String pair, BigDecimal quantity, BigDecimal price, String orderSide,
                                              String execType, String orderType, String orderStatus) {
    return createTestOrder(pair, quantity, price, orderSide, execType, orderStatus, getClosingStop("sell".equalsIgnoreCase(orderSide), orderType));
  }*/

  @NotNull
  public static OrderResponse createTestOrder(String pair, BigDecimal quantity, BigDecimal price, String orderSide,
                                              String execType, String stopType, String orderStatus) {
    OrderResponse order = new OrderResponse();
    order.setSymbol(pair);
    order.setSize(quantity);
    order.setPrice(price);
    order.setSide(orderSide);
    //order.setPositionSide(positionSide.name());
    if (stopType != null) {
      order.setStop(stopType);
      order.setCloseOrder(true);
    }
    order.setType(execType);
    order.setStatus(orderStatus);
    order.setOrderTime(DateUtils.currentTimeMillis());
    order.setClientOid(generateTestOrderId());
    LOGGER.debug("Test order on {} created: amount - {} price - {}",
        order.getSymbol(), order.getSize(), order.getPrice());
    return order;
  }

  private static String generateTestOrderId() {
    /*byte[] array = new byte[24]; // length is limited by 24
    new Random().nextBytes(array);
    return new String(array, Utils.DEFAULT_CHARSET);*/
    return "test" + UUID.randomUUID().toString().replaceAll("-", "");
  }

  @NotNull
  protected static String getClosingOrderSide(boolean buy) {
    return buy ? KucoinSignedClient.OrderSide.SELL : KucoinSignedClient.OrderSide.BUY;
  }

  protected static String getClosingStop(boolean buy, String orderType) {
    if (OrderInfoObject.Type.STOP_LOSS.equals(orderType)) {
      return buy ? "down" : "up";
    } else if (OrderInfoObject.Type.TAKE_PROFIT.equals(orderType)) {
      return buy ? "up" : "down";
    }
    return null;
  }

  private List<OrderResponse> openOrders = null;
  private long openOrdersTs = 0L;
  protected List<OrderResponse> getOpenOrders(KucoinSignedClient apiClient, String symbol) throws Exception {
    if (testOrders)
      return null;
    //todo кеширование должно быть разделено для разных клиентов if (openOrders == null || isOpenOrdersCacheExpired()) {
    // можно просто создавать свой OrdersProcessor для каждого трейдера
    openOrders = apiClient.getOrders(true, symbol);
    openOrdersTs = DateUtils.currentTimeMillis();
    LOGGER.debug("Open orders loaded count - {}", openOrders.size());
    for (OrderResponse o : openOrders) {
      LOGGER.trace(o.toString());
    }
    //}
    return openOrders;
  }

  private boolean isOpenOrdersCacheExpired() {
    return DateUtils.currentTimeMillis() - openOrdersTs > OPEN_ORDERS_CACHE_LIVE_TIME;
  }

  /*public KucoinOrdersProcessor.PositionContainer createPositionContainer(Object order) {
    KucoinOrdersProcessor.PositionContainer result = new KucoinOrdersProcessor.PositionContainer();
    result.setPositionInfo(preparePositionInfo(order));
    result.addOrder(convertOrder(order, result.getPositionInfo().getPositionId()));
    return result;
  }*/

  /*private static String formatDecimal(double value) {
    return Utils.formatFloatValue((float) value, 4);
  }*/

  public static class PositionContainer extends FuturesPositionContainer<KucoinSignedClient> {
    private final AtomicInteger safetyOrdersTriggered = new AtomicInteger(0);

    public PositionContainer() {

    }

    public PositionContainer(PositionInfo position) {
      this.position = (PositionInfoObject) position;
    }

    /*public PositionContainer(Object baseOrder) {
      addOrder(getOrdersProcessor().convertOrder(baseOrder, null));
    }*/

    @Deprecated
    public OrderInfoObject getBaseOrder() {
      return getOrders().isEmpty() ? null : getOrders().get(0);
    }

    public List<OrderInfoObject> getSafetyOrders() {
      return new ArrayList<>(orders);
    }

    public boolean removeSafetyOrder(OrderInfoObject order) {
      return orders.remove(order);
    }

    public int incrementSafetyOrdersTriggered() {
      return safetyOrdersTriggered.incrementAndGet();
    }

    protected KucoinOrdersProcessor getOrdersProcessor() {
      return getInstance(false);
    }

    public boolean newSafetyOrdersFilled(boolean updateCounter) {
      int counter = 0;
      try {
        for (OrderInfoObject o : orders) {
          if (OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()))
            counter++;
        }
        return safetyOrdersTriggered.get() < counter;
      } finally {
        if (updateCounter && counter > 0)
          safetyOrdersTriggered.set(counter);
      }
    }

    @Override
    public boolean someOrdersFilled() {
      for (OrderInfoObject o : orders) {
        if (OrderInfoObject.OrderStatus.FILLED.equals(o.getStatus()))
          return true;
      }
      return false;
    }

    @Override
    public OrderInfoObject closeDeal(KucoinSignedClient apiAccess) {
      if (apiAccess == null) {
        LOGGER.warn("Needed API connector aren't provided");
        return null;
      }
      OrderResponse result;
      try {
        // надо как-то исправить метод так, чтобы для тестовых выполнялся "фейковый" update (статус позиции)
        //upd: убираю отсюда update, поскольку не помню, зачем он тут нужен
        //update(apiAccess);
        if (!getPositionInfo().onStatusOpen()) {
          LOGGER.info("Position isn't open yet, will try to cancel instead of closing");
          if (cancel(apiAccess) == 0)
            LOGGER.warn("Unsuccessful order cancelling");
          return null;
        }

        if (!getOrdersProcessor().cancelOrder(apiAccess, takeProfitOrder))
          LOGGER.warn("Unsuccessful take-profit order cancelling");
        if (!getOrdersProcessor().cancelOrder(apiAccess, stopLossOrder))
          LOGGER.warn("Unsuccessful stop-loss order cancelling");

        result = getOrdersProcessor().closeDeal(apiAccess,
              getPositionInfo().getSymbol(), getExecutedAmount(),
            Consts.BUY.equals(getPositionInfo().getDirection()));
      } catch (Exception e) {
        LOGGER.error("Failed to close {} deal for {} {} by market",
            getPositionInfo().getDirection(), getExecutedAmount(), getPositionInfo().getSymbol(), e);
        return null;
      }
      return getOrdersProcessor().convertOrder(result, getPositionInfo().getPositionId());
    }

    @Override
    public OrderInfoObject closePartially(KucoinSignedClient apiAccess, BigDecimal amount) {
      if (apiAccess == null) {
        LOGGER.warn("Expected API connector aren't provided");
        return null;
      }
      OrderResponse result;
      try {
        result = getOrdersProcessor().closeDeal(apiAccess,
            getPositionInfo().getSymbol(), amount,
            Consts.BUY.equals(getPositionInfo().getDirection()));
      } catch (Exception e) {
        LOGGER.error("Failed to close {} deal partially for {} {} by market",
            getPositionInfo().getDirection(), amount, getPositionInfo().getSymbol(), e);
        return null;
      }
      return getOrdersProcessor().convertOrder(result, getPositionInfo().getPositionId());
    }

    /*@Override вынесено в предка
    public int update(KucoinSignedClient apiAccess) throws UnknownHostException {
      int result = 0;
      if (apiAccess == null)
        return result;
      LOGGER.debug("Updating position container with id = {}", position.getPositionId());

      if (!getOrdersProcessor().testOrders) {
        if (getStopLossOrder() != null) {
          OrderResponse slUpdated = getOrdersProcessor().updateOrder(apiAccess, getStopLossOrder());
          if (slUpdated != null) {
            stopLossOrder = getOrdersProcessor().convertOrder(slUpdated, getPositionInfo().getPositionId());
            result++;
          }
        }
        if (getTakeProfitOrder() != null) {
          OrderResponse tpUpdated = getOrdersProcessor().updateOrder(apiAccess, getTakeProfitOrder());
          if (tpUpdated != null) {
            takeProfitOrder = getOrdersProcessor().convertOrder(tpUpdated, getPositionInfo().getPositionId());
            result++;
          }
        }

        for (OrderInfoObject order : new ArrayList<>(orders)) {
          OrderInfoObject safetyUpdated = getOrdersProcessor().convertOrder(
              getOrdersProcessor().updateOrder(apiAccess, order), getPositionInfo().getPositionId());
          if (safetyUpdated != null) {
            orders.remove(order);
            orders.add(safetyUpdated);
            result++;
          }
        }
      }
      updatePositionStatus();

      return result;
    }*/
	
    @Override
    public int cancel(KucoinSignedClient apiAccess) {
      int result = 0;
      if (apiAccess == null)
        return result;

      if (position != null)
        LOGGER.debug("Cancelling position container with id = {}", position.getPositionId());
      boolean slCancelled = getOrdersProcessor().cancelOrder(apiAccess, getStopLossOrder());
      if (slCancelled)
        result++;
      boolean tpCancelled = getOrdersProcessor().cancelOrder(apiAccess, getTakeProfitOrder());
      if (tpCancelled)
        result++;

      for (OrderInfoObject order : orders) {
        boolean mainCancelled = getOrdersProcessor().cancelOrder(apiAccess, order);
        if (mainCancelled)
          result++;
      }

      return result;
    }

    @Override
    public boolean cancelSafetyOrders(KucoinSignedClient apiAccess) {
      if (apiAccess == null)
        return false;
      boolean result = true;
      for (OrderInfoObject order : new ArrayList<>(orders)) {
        if (!OrderInfoObject.OrderStatus.FILLED.equals(order.getStatus())) {
          boolean safetyCancelled = getOrdersProcessor().cancelOrder(apiAccess, order);
          if (!safetyCancelled) {
            result = false;
          } else {
            orders.remove(order);
          }
        }
      }

      return result;
    }

    @Override
    public boolean rearrangeTakeProfit(KucoinSignedClient apiAccess, @Nullable BigDecimal newTp) {
      if (apiAccess == null) {
        LOGGER.warn("Can't rearrange take-profit: no valid client provided");
        return false;
      }
      LOGGER.debug("rearrangeTakeProfit, new value = {}", newTp);
      if (newTp == null) {
        LOGGER.warn("Can't rearrange TP order {}: no value provided", getTakeProfitOrder().getOrderId());
        return false;
      }
      boolean removeOnly = newTp.floatValue() == 0;
      //существующий TP отменится в placeTakeProfit
      /*if (getTakeProfitOrder() != null) {
        Order tp = getOrdersProcessor().cancelOrder((SignedClient) apiAccess, getTakeProfitOrder());
        if (tp == null)
          return null;
      }*/
      try {
        OrderResponse newTPOrder = getOrdersProcessor().placeTakeProfit(apiAccess,
            getOrdersProcessor().getOpenOrders(apiAccess, position.getSymbol()),
            KucoinFuturesApiProvider.getSymbolInfo(position.getSymbol()), this, newTp.floatValue());
        if (!removeOnly && newTPOrder == null)
          throw new NullPointerException("New TP order is null");
        if (takeProfitOrder != null) {
        try {
            LOGGER.trace("Updating outdated take-profit order {}", takeProfitOrder.getOrderId());
          this.outdatedOrders.add(getOrdersProcessor().convertOrder(
              getOrdersProcessor().updateOrder(apiAccess, takeProfitOrder), getPositionInfo().getPositionId()));
        } catch (Exception e) {
          LOGGER.warn("Updating outdated TP order with id = {} failed", takeProfitOrder.getOrderId());
          }
        }
        this.takeProfitOrder = removeOnly ? null : getOrdersProcessor().convertOrder(newTPOrder, getPositionInfo().getPositionId());
        /*LOGGER.debug(String.format("Set a %s take profit order with id %s for base order %s",
            newTPOrder.getSide(), newTPOrder.getClientOrderId(), baseOrder.getClientOrderId()));*/
        return true;
      } catch (Exception e) {
        LOGGER.error("Failed {} TP order for {} {} by {}", position.getDirection(), position.getAmount(), position.getSymbol(), newTp, e);
        return false;
      }
    }

    @Override
    public Object rearrangeStopLoss(KucoinSignedClient apiAccess, BigDecimal newSl) {
      if (apiAccess == null)
        return null;
      LOGGER.debug("rearrangeStopLoss, new value = {}", newSl);
      if (newSl == null) {
        LOGGER.warn("Can't rearrange SL order {}: no value provided", getStopLossOrder().getOrderId());
        return null;
      }
      //todo существующий SL не отменяется в placeStopLoss, отменится в трейдере
      /*if (getStopLossOrder() != null) {
        Order sl = getOrdersProcessor().cancelOrder((SignedClient) apiAccess, getStopLossOrder());
        if (sl == null)
          return null;
      }*/
      try {
        OrderResponse newSlOrder = getOrdersProcessor().placeStopLoss(apiAccess,
            getOrdersProcessor().getOpenOrders(apiAccess, position.getSymbol()),
            KucoinFuturesApiProvider.getSymbolInfo(position.getSymbol()), this, newSl.floatValue());
        if (newSlOrder == null)
          throw new NullPointerException("New SL order is null");
        if (stopLossOrder != null) {
        try {
            LOGGER.trace("Updating outdated stop-loss order {}", stopLossOrder.getOrderId());
          this.outdatedOrders.add(getOrdersProcessor().convertOrder(
              getOrdersProcessor().updateOrder(apiAccess, stopLossOrder), getPositionInfo().getPositionId()));
        } catch (Exception e) {
          LOGGER.warn("Updating outdated SL order with id = {} failed", stopLossOrder.getOrderId());
          }
        }
        this.stopLossOrder = getOrdersProcessor().convertOrder(newSlOrder, getPositionInfo().getPositionId());
        /*LOGGER.debug(String.format("Set a %s stop loss order with id %s for base order %s",
            newSlOrder.getSide(), newSlOrder.getClientOrderId(), baseOrder.getClientOrderId()));*/
        return newSlOrder;
      } catch (Exception e) {
        LOGGER.error("Failed {} SL order for {} {} by {}",
            position.getDirection(), position.getAmount(), position.getSymbol(), newSl, e);
        return null;
      }
    }

    @Override
    public String toString() {
      return toString(null);
    }
  }
}