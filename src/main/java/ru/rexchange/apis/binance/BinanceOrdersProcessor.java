package ru.rexchange.apis.binance;

import binance.futures.enums.*;
import binance.futures.model.Candle;
import binance.futures.model.ExchangeInfoEntry;
import binance.futures.model.Order;
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
import ru.rexchange.trading.trader.AbstractSignedClient;
import ru.rexchange.trading.trader.BinanceSignedClient;
import ru.rexchange.trading.trader.futures.FuturesPositionContainer;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BinanceOrdersProcessor extends AbstractOrdersProcessor {
  protected static Logger LOGGER = LoggerFactory.getLogger(BinanceOrdersProcessor.class);
  private static BinanceOrdersProcessor instance = null;
  private static final long OPEN_ORDERS_CACHE_LIVE_TIME = 60 * 1000L;
  private boolean testOrders = false;
  /*public static final List<String> NOT_EXECUTED_ORDER_STATUSES = Arrays.asList(OrderStatus.NEW.name(),
      OrderStatus.CANCELED.name(), OrderStatus.EXPIRED.name());*/

  protected BinanceOrdersProcessor(boolean testOrders) {
    this.testOrders = testOrders;
  }

  public static BinanceOrdersProcessor getInstance(boolean testOrders) {
    if (instance == null) {
      instance = new BinanceOrdersProcessor(testOrders);
    }
    return instance;
  }

  public void setTestOrders(boolean value) {
    testOrders = value;
  }

  @Override
  public OrderInfoObject limitOrder(AbstractSignedClient aClient, float price, String pair,
                                    double amount, boolean buy, Integer leverage) throws UserException {
    LOGGER.debug(String.format("Placing a %s limit order for %s", buy ? "buy" : "sell", pair));
    BinanceSignedClient apiClient = (BinanceSignedClient) aClient;
    ExchangeInfoEntry symbolInfo = BinanceFuturesApiProvider.getSymbolInfo(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, amount);
    String priceStr = checkAndFitPrice(symbolInfo, price, 0);

    try {
      Order order = buy ?
          openBuy(apiClient, true, pair, convertedAmount, priceStr) :
          openSell(apiClient, true, pair, convertedAmount, priceStr);
      LOGGER.debug(String.format("Posted a limit %s order with id %s for %s %s by %s",
          order.getSide(), order.getClientOrderId(), convertedAmount, pair, priceStr));
      return convertOrder(order, null);
    } catch (Exception e) {
      LOGGER.error(String.format("Place %s order failed: %s %s", (buy ? "buy" : "sell"), convertedAmount, pair));
      throw new SystemException(e);
    }
  }

  @Override
  public AbstractPositionContainer createEmptyPositionContainer() {
    return new PositionContainer();
  }

  @Override
  public OrderInfoObject queryOrder(AbstractSignedClient client, String symbol, String orderId) throws UserException {
    BinanceSignedClient apiClient = (BinanceSignedClient) client;
    try {
      return convertOrder(apiClient.queryOrder(symbol, null, orderId), null);
    } catch (Exception e) {
      throw new UserException("Error occurred while querying order " + orderId, e);
    }
  }

  @Override
  public OrderInfoObject convertOrder(Object customOrder, String positionId) {
    if (customOrder == null)
      return null;
    OrderInfoObject result = new OrderInfoObject();
    fillOrderObject(result, (Order) customOrder, positionId);

    return result;
  }

  public static OrderInfoObject fillOrderObject(OrderInfoObject orderObj, Order order, String parentId) {
    orderObj.setOrderId(order.getClientOrderId());
    orderObj.setExternalId(String.valueOf(order.getOrderId()));
    orderObj.setDirection(order.getSide());
    //TODO привести типы ордеров к единому виду и не забыть про updateOrder в BackTestingOrdersProcessor
    orderObj.setType(defineType(order));
    if (orderObj.getType() == null)
      orderObj.setType(order.getType());
    orderObj.setPositionSide(order.getPositionSide());
    //orderObj.setBaseIndex(baseCurrency);
    //orderObj.setQuotedIndex(quotedCurrency);
    orderObj.setSymbol(order.getSymbol());
    orderObj.setAvgPrice(order.getAvgPrice());
    orderObj.setExecutedAmount(order.getExecutedQty());
    orderObj.setPrice(order.getAvgPrice().floatValue());
    if (orderObj.getPrice() == 0)
      orderObj.setPrice(order.getPrice().floatValue());
    if (orderObj.getPrice() == 0)
      orderObj.setPrice(order.getStopPrice().floatValue());
    orderObj.setStatus(order.getStatus());
    if (BigDecimal.ZERO.compareTo(order.getExecutedQty()) == 0)
      orderObj.setAmount(order.getOrigQty().doubleValue());
    else
      orderObj.setAmount(order.getExecutedQty().doubleValue());
    orderObj.setPositionId(parentId);
    orderObj.setOrderTimestamp(new Timestamp(order.getUpdateTime()));
    return orderObj;
  }

  private static String defineType(Order order) {
    //todo пока считаем, что базовому рыночному ордеру всё равно дальше проставится нужный тип
    if (OrderType.MARKET.name().equals(order.getType()))
      return OrderInfoObject.Type.CLOSE_ORDER;
        //&& getClosingOrderSide(buy), getClosingPositionSide(buy),
    return null;
  }

  @Override
  public AbstractPositionContainer placeOrder(AbstractSignedClient aClient, AbstractPositionContainer openPosition, boolean limit, float price, String pair, double amount, Integer leverage,
                                              boolean buy, @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    LOGGER.debug(String.format("Placing a %s limit order for %s", buy ? "buy" : "sell", pair));
    BinanceSignedClient apiClient = (BinanceSignedClient) aClient;
    if (leverage != null && !testOrders) {
        setLeverageSafe(apiClient, pair, leverage);
    }
    ExchangeInfoEntry symbolInfo = BinanceFuturesApiProvider.getSymbolInfo(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, amount);
    String priceStr = checkAndFitPrice(symbolInfo, price,
        buy ? getLimitOrderShiftInTicks() : -getLimitOrderShiftInTicks());

    try {
      Order order = buy ?
          openBuy(apiClient, limit, pair, convertedAmount, priceStr) :
          openSell(apiClient, limit, pair, convertedAmount, priceStr);
      LOGGER.debug(String.format("Executed a %s order with id %s for %s %s by %s",
              order.getSide(), order.getClientOrderId(), convertedAmount, pair, priceStr));
      if (!OrderStatus.FILLED.name().equals(order.getStatus())) {
        LOGGER.info(String.format("Order is %s, not %s!", order.getStatus(), OrderStatus.FILLED.name()));
      }
      PositionContainer result;

      if (openPosition == null) {
        result = (PositionContainer) (createPositionContainer(convertOrder(order, null)));
      } else {
        result = (PositionContainer) openPosition;
        openPosition.addOrder(convertOrder(order, openPosition.getPositionInfo().getPositionId()));
      }
      Order takeProfitOrder, stopLossOrder = null;
      //TODO если это наращивание позиции, то TP и SL должны создаваться на весь размер позиции,
      // а не только на размер последнего ордера!!!
      List<Order> orders = getOpenOrders(apiClient);
      if (stopLoss != null && !Float.isNaN(stopLoss)) {
        stopLossOrder = placeStopLoss(apiClient, orders, symbolInfo, result, stopLoss);
        if (stopLossOrder == null && cancelOrder(apiClient, order) != null) {
          return null;
        }
        result.setStopLossOrder(convertOrder(stopLossOrder, result.getPositionInfo().getPositionId()));
      }
      if (takeProfit != null && !Float.isNaN(takeProfit)) {
        takeProfitOrder = placeTakeProfit(apiClient, orders, symbolInfo, result, takeProfit);
        if (takeProfitOrder == null && cancelOrder(apiClient, order) != null) {
          cancelOrder(apiClient, stopLossOrder);
          return null;
        }
        result.setTakeProfitOrder(convertOrder(takeProfitOrder, result.getPositionInfo().getPositionId()));
      }

      return result;
    } catch (Exception e) {
      LOGGER.error(String.format("Place %s order failed: %s %s", (buy ? "buy" : "sell"), convertedAmount, pair));
      throw new SystemException(e);
    }
  }

  @Override
  public AbstractPositionContainer placeMarketOrder(AbstractSignedClient aClient, AbstractPositionContainer openPosition,
                                                    String pair, double amount, Integer leverage,
                                                    boolean buy) throws UserException {
    LOGGER.debug(String.format("Placing a %s limit order for %s", buy ? "buy" : "sell", pair));
    BinanceSignedClient apiClient = (BinanceSignedClient) aClient;
    if (leverage != null && !testOrders) {
        setLeverageSafe(apiClient, pair, leverage);
    }
    ExchangeInfoEntry symbolInfo = BinanceFuturesApiProvider.getSymbolInfo(pair);

    try {
      float lastPrice = getLastPrice(pair);
      String convertedAmount = checkAndFitAmount(symbolInfo, lastPrice, amount);
      /*String priceStr = checkAndFitPrice(symbolInfo, price,
          buy ? getLimitOrderShiftInTicks() : -getLimitOrderShiftInTicks());*/

      Order order = buy ?
          openBuy(apiClient, false, pair, convertedAmount, null) :
          openSell(apiClient, false, pair, convertedAmount, null);
      LOGGER.debug(String.format("Executed a %s order with id %s for %s %s by %s",
              order.getSide(), order.getClientOrderId(), convertedAmount, pair, lastPrice));
      if (!OrderStatus.FILLED.name().equals(order.getStatus())) {
        LOGGER.info(String.format("Order is %s, not %s!", order.getStatus(), OrderStatus.FILLED.name()));
      }
      if (openPosition == null)
        return createPositionContainer(convertOrder(order, null));
      else {
        openPosition.addOrder(convertOrder(order, null));
        return openPosition;
      }
    } catch (Exception e) {
      LOGGER.error(String.format("Place %s order on %s failed", (buy ? "buy" : "sell"), pair));
      throw new SystemException(e);
    }
  }

  /**
   * Round amount to base precision and LOT_SIZE
   *
   * @param price
   * @param amount
   * @return
   */
  protected String checkAndFitAmount(ExchangeInfoEntry symbolInfo, float price, double amount) throws UserException {
    String convertedAmount = String.valueOf(amount);
    BigDecimal originalDecimal = new BigDecimal(convertedAmount);
    if (convertedAmount.length() > 6)
      convertedAmount = convertedAmount.substring(0, 6);
    if (symbolInfo != null) {
      long precision = symbolInfo.getBaseAssetPrecision();
      List<Map<String, String>> filters = symbolInfo.getFilters();//TODO magic consts
      String minQty = getFilterValue(filters, "LOT_SIZE", "minQty");
      String minNotational = getFilterValue(filters, "MIN_NOTIONAL", "notional");//минимальная сумма с учётом плеча

      if (minQty != null) {
        double minQtyDouble = Double.parseDouble(minQty);
        //Check LOT_SIZE to make sure amount is not too small
        if (amount < minQtyDouble) {
          throw new UserException("Amount (%s) smaller than min LOT_SIZE (%s) for %s, could not open trade!",
              amount, minQty, symbolInfo.getSymbol());
        }

        //Convert amount to an integer multiple of LOT_SIZE and convert to asset precision
        LOGGER.trace(String.format("Converting from double trade amount %s to base asset precision %s LOT_SIZE - %s",
            originalDecimal, precision, minQty));
        //TODO проверить эти формулы и упростить, если возможно
        convertedAmount = new BigDecimal(minQty).multiply(new BigDecimal(amount / minQtyDouble)).
            setScale(new BigDecimal(minQty).scale(), RoundingMode.HALF_DOWN).
            //может вставить E stripTrailingZeros().
            toString();
        LOGGER.trace("Converted to " + convertedAmount);

        if (minNotational != null) {
          double notational = Double.parseDouble(convertedAmount) * price;
          if (notational < Double.parseDouble(minNotational)) {
            throw new UserException("Cannot open trade because notational value %s %s is smaller than minimum deal value %s %s",
                formatDecimal(notational), symbolInfo.getQuoteAsset(), minNotational, symbolInfo.getQuoteAsset());
          }
        }
      } else {
        LOGGER.warn("Couldn't get min lot size, will try to open deal anyway");
      }
    }
    return convertedAmount;
  }

  protected String checkAndFitPrice(ExchangeInfoEntry symbolInfo, float price, int shift) {
    String preparedPrice = String.valueOf(price);
    LOGGER.trace("Preparing order price value: " + price);
    if (symbolInfo != null) {
      List<Map<String, String>> filters = symbolInfo.getFilters();//TODO magic consts
      String tickSize = getFilterValue(filters, "PRICE_FILTER", "tickSize");
      preparedPrice = checkByRange(getFilterValue(filters, "PRICE_FILTER", "minPrice"),
          getFilterValue(filters, "PRICE_FILTER", "maxPrice"), price, symbolInfo.getSymbol());
      if (tickSize != null)
        preparedPrice = roundAndShiftPriceByTickSize(preparedPrice, tickSize, shift);
    } else {
      if (preparedPrice.length() > 7)
        preparedPrice = preparedPrice.substring(0, 7);
    }
    LOGGER.trace("Fitted price value is " + preparedPrice);
    return preparedPrice;
  }

  private static String roundAndShiftPriceByTickSize(String price, String tickSize, int shift) {
    BigDecimal pbd = new BigDecimal(price);
    BigDecimal tbd = new BigDecimal(tickSize).stripTrailingZeros();
    BigDecimal result = pbd.setScale(tbd.scale(), RoundingMode.HALF_EVEN);
    if (shift != 0) {
      result = result.add(tbd.multiply(BigDecimal.valueOf(shift)));
    }
    return result.toString();
  }

  private String roundAndShiftPriceByTickSizeAlt(String price, String tickSize, int shift) {
    //TODO альтернативный потенциально более быстрый подход:
    //1. Находим точку в строке цены и в строке tickSize
    //2. В цикле идём по символам после точки в tickSize, пока не дойдём до символа, отличного от 0
    //3. На этом же индексе обрезаем исходную цену
    //4. Можно тупо изменять значение последней цифры в + или - в зависимости от shift, если есть куда изменять
    return price;
  }

    protected String checkByRange(String minValue, String maxValue, float price, String symbol) {
    if (minValue == null || maxValue == null) {
      LOGGER.warn(String.format("Can not check price value %s. No range provided", price));
      return String.valueOf(price);
    }
    Float fMin = Float.valueOf(minValue),
        fMax = Float.valueOf(maxValue);
    if (price > fMin && price < fMax)
      return String.valueOf(price);
    if (price < fMin) {
      LOGGER.warn(String.format("Order price is too small - %s. Returning minimum range value for %s: %s",
          price, symbol, fMin));
      return String.valueOf(fMin);
    }
    if (price > fMax) {
      LOGGER.warn(String.format("Order price is too big - %s. Returning maximum range value for %s: %s",
          price, symbol, fMax));
      return String.valueOf(fMax);
    }
    return String.valueOf(price);
  }

  private static String getFilterValue(List<Map<String, String>> filters, String filterType, String field) {
    for (Map<String, String> filter : filters) {
      if (filterType.equals(filter.get("filterType")))
        return filter.get(field);
    }
    return null;
  }

  public static String setLeverage(BinanceSignedClient apiClient, String pair, Integer leverage) throws Exception {
    LOGGER.debug(String.format("Setting leverage for pair %s to %s", pair, leverage));
    return apiClient.setLeverage(pair, leverage);
  }

  public static String setLeverageSafe(BinanceSignedClient apiClient, String pair, Integer leverage) {
    try {
      return setLeverage(apiClient, pair, leverage);
    } catch (Exception e) {
      LOGGER.error("Unsuccessful leverage change for pair " + pair, e);
      return null;
    }
  }

  private Order openBuy(BinanceSignedClient client, boolean limit, String pair, String quantity, String price) throws Exception {
    // place dual position side order.
    // Switch between dual or both position side, call: com.binance.client.examples.trade.ChangePositionSide
    if (testOrders) {
      return createTestOrder(pair, quantity, price, OrderSide.BUY, PositionSide.LONG,
          limit ? OrderType.LIMIT : OrderType.MARKET, limit ? OrderStatus.NEW : OrderStatus.FILLED);
    } else if (limit) {
      return client.postOrder(pair, OrderSide.BUY, PositionSide.LONG, OrderType.LIMIT, TimeInForce.GTC,
          quantity, price, null, null, null, null,
          NewOrderRespType.RESULT, null);
    } else {
      return client.postOrder(pair, OrderSide.BUY, PositionSide.LONG, OrderType.MARKET, null,
          quantity, null, null, null, null, WorkingType.CONTRACT_PRICE,
          NewOrderRespType.RESULT, null);
    }
  }

  private Order openSell(BinanceSignedClient client, boolean limit, String pair, String quantity, String price) throws Exception {
    // place dual position side order.
    // Switch between dual or both position side, call: com.binance.client.examples.trade.ChangePositionSide
    if (testOrders) {
      return createTestOrder(pair, quantity, price, OrderSide.SELL, PositionSide.SHORT,
          limit ? OrderType.LIMIT : OrderType.MARKET, limit ? OrderStatus.NEW : OrderStatus.FILLED);
    } else if (limit) {
      return client.postOrder(pair, OrderSide.SELL, PositionSide.SHORT, OrderType.LIMIT, TimeInForce.GTC,
          quantity, price, null, null, null, null,
          NewOrderRespType.RESULT, null);
    } else {
      return client.postOrder(pair, OrderSide.SELL, PositionSide.SHORT, OrderType.MARKET, null,
          quantity, null, null, null, null, WorkingType.CONTRACT_PRICE,
          NewOrderRespType.RESULT, null);
    }
  }

  protected Order closeDeal(BinanceSignedClient client, String pair, BigDecimal quantity, boolean buy) throws Exception {
    ExchangeInfoEntry symbolInfo = BinanceFuturesApiProvider.getSymbolInfo(pair);
    float price = getLastPrice(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, quantity.doubleValue());
    if (testOrders) {
      return createTestOrder(pair, convertedAmount, checkAndFitPrice(symbolInfo, price, 0),
          getClosingOrderSide(buy), getClosingPositionSide(buy),
          OrderType.STOP_MARKET, OrderStatus.FILLED);
    } else {
      return client.postOrder(pair, getClosingOrderSide(buy), getClosingPositionSide(buy),
          OrderType.MARKET, null, convertedAmount, null, null, null,
          null, WorkingType.CONTRACT_PRICE, NewOrderRespType.RESULT, null);
    }
  }

  protected Order placeTakeProfit(BinanceSignedClient apiClient, List<Order> orders, ExchangeInfoEntry symbolInfo,
                                  PositionContainer position, float takeProfitValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = OrderSide.BUY.name().equals(positionInfo.getDirection());
    String priceStr = String.valueOf(takeProfitValue);
    //todo несколько попыток
    //TODO возможность установки трейлинг стопа (percentage должен зависеть от riskLevel)
    try {
      //Отменяем существующий take-profit при наличии
      if (checkForExistingTakeProfit(apiClient, orders, buy, positionInfo.getSymbol()) != null) {//todo unknown order sent
        LOGGER.debug(String.format("Trying to recreate TP order with new price %s", takeProfitValue));
      }
      priceStr = checkAndFitPrice(symbolInfo, takeProfitValue, 0);
      Order order;
      if (testOrders) {
        order = createTestOrder(positionInfo.getSymbol(), String.valueOf(positionInfo.getAmount()),
            priceStr, getClosingOrderSide(buy), getClosingPositionSide(buy), OrderType.TAKE_PROFIT_MARKET);
        order.setStatus(OrderStatus.NEW.name());
      } else {
        //todo An open stop ... in the direction is existing
        //todo Order would immediately trigger (в таком случае позиция остаётся без TP вовсе)
        order = apiClient.postOrder(positionInfo.getSymbol(), getClosingOrderSide(buy), getClosingPositionSide(buy),
            OrderType.TAKE_PROFIT_MARKET, TimeInForce.GTE_GTC,
            String.valueOf(positionInfo.getAmount()), null, null, null, priceStr,
            WorkingType.CONTRACT_PRICE, NewOrderRespType.RESULT, true);
      }
      LOGGER.debug(String.format("Set a %s take profit order with id %s for base order %s",
          order.getSide(), order.getClientOrderId(), positionInfo.getPositionId()));
      return order;
    } catch (Exception e) {
      //TODO e.getMessage().contains("is existing") - значит уже существует TP для позиции
      LOGGER.error(String.format("Failed %s TP order for %s %s by %s",
              positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), priceStr), e);
      return null;
    }
  }

  private Float checkForExistingTakeProfit(BinanceSignedClient apiClient, List<Order> openOrders,
                                           boolean buy, String symbol) {
    Order existingTakeProfit = findExistingOrder(openOrders,
        symbol, getClosingOrderSide(buy), getClosingPositionSide(buy), OrderType.TAKE_PROFIT_MARKET);
    if (existingTakeProfit != null) {
      float stopPrice = existingTakeProfit.getStopPrice().floatValue();
      LOGGER.debug(String.format("Existing TP at %s found", stopPrice));
      if (cancelOrder(apiClient, existingTakeProfit) == null)
        throw new SystemException("Unsuccessful TP order cancellation " + existingTakeProfit.getClientOrderId());
      return stopPrice;
    }
    return null;
  }

  protected Order placeStopLoss(BinanceSignedClient apiClient, List<Order> orders, ExchangeInfoEntry symbolInfo,
                                PositionContainer position, float stopLossValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = OrderSide.BUY.name().equals(positionInfo.getDirection());
    //todo несколько попыток
    String priceStr = String.valueOf(stopLossValue);
    try {
      //Отменяем существующий stop-loss при наличии
      checkForExistingStopLoss(apiClient, orders, buy, positionInfo.getSymbol());//todo unknown order sent
      priceStr = checkAndFitPrice(symbolInfo, stopLossValue, 0);
      Order order;
      if (testOrders) {
        order = createTestOrder(positionInfo.getSymbol(), String.valueOf(positionInfo.getAmount()),
            priceStr, getClosingOrderSide(buy), getClosingPositionSide(buy), OrderType.STOP_MARKET);
        order.setStatus(OrderStatus.NEW.name());
      } else {
        //todo An open stop ... in the direction is existing
        order = apiClient.postOrder(positionInfo.getSymbol(), getClosingOrderSide(buy), getClosingPositionSide(buy),
            OrderType.STOP_MARKET, TimeInForce.GTE_GTC,
            String.valueOf(positionInfo.getAmount()), null, null, null, priceStr,
            WorkingType.MARK_PRICE, NewOrderRespType.RESULT, true);
      }
      LOGGER.debug(String.format("Set a %s stop loss order with id %s for base order %s",
          order.getSide(), order.getClientOrderId(), positionInfo.getPositionId()));
      return order;
    } catch (Exception e) {
      //TODO e.getMessage().contains("is existing") - значит уже существует SL для позиции
      LOGGER.error(String.format("Failed %s SL order for %s %s by %s",
          positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), priceStr), e);
      return null;
    }
  }

  private Float checkForExistingStopLoss(BinanceSignedClient apiClient, List<Order> openOrders,
                                         boolean buy, String symbol) {
    Order existingStopLoss = findExistingOrder(openOrders,
        symbol, getClosingOrderSide(buy), getClosingPositionSide(buy), OrderType.STOP_MARKET);
    if (existingStopLoss != null) {//логика корректировки стоп-лосса
      float stopPrice = existingStopLoss.getStopPrice().floatValue();
      LOGGER.debug(String.format("Existing SL at %s found", stopPrice));
      if (cancelOrder(apiClient, existingStopLoss) == null)
        throw new SystemException("Unsuccessful SL order cancellation " + existingStopLoss.getClientOrderId());
      return stopPrice;
    }
    return null;
  }

  @Override
  public float getLastPrice(String symbol) throws Exception {
    Candle priceData = BinanceFuturesApiProvider.getLastPrice(symbol);
    if (priceData == null)
      throw new SystemException("Last price request for symbol %s failed", symbol);
    return priceData.getClosePrice();
  }

  protected Order cancelOrder(AbstractSignedClient apiClient, Order order) {
    if (order == null) {
      LOGGER.warn("Attempt to close non-existing order");
      return null;
    }
    if (!OrderStatus.NEW.name().equals(order.getStatus())) {//TODO а что делать в противном случае?
      LOGGER.warn(String.format("Order %s is %s, can not be canceled!", order.getClientOrderId(), order.getStatus()));
      if (OrderStatus.CANCELED.name().equals(order.getStatus()))
        return order;
      return null;
    }
    LOGGER.debug(String.format("Cancelling %s order %s for %s by %s on %s",
        order.getSide(), order.getClientOrderId(), order.getOrigQty(), order.getPrice(), order.getSymbol()));
    if (testOrders) {
      LOGGER.debug(String.format("Test order on %s cancelled: amount - %s price - %s",
          order.getSymbol(), order.getOrigQty(), order.getPrice()));
      order.setStatus(OrderStatus.CANCELED.name());
      return order;
    }
    try {
      return Utils.executeInFewAttempts (() ->
          ((BinanceSignedClient) apiClient).cancelOrder(order.getSymbol(), order.getOrderId(), order.getClientOrderId()),
          3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to cancel %s order %s for %s by %s on %s",
          order.getSide(), order.getClientOrderId(), order.getOrigQty(), order.getPrice(), order.getSymbol()), e);
      return null;
    }
  }

  //TODO разобраться с дулированием метода cancelOrder

  public boolean cancelOrder(AbstractSignedClient apiClient, OrderInfoObject order) {
    if (order == null) {
      LOGGER.warn("Attempt to close non-existing order");
      return false;
    }
    if (!OrderInfoObject.OrderStatus.NEW.equals(order.getStatus())) {//TODO а что делать в противном случае?
      LOGGER.warn(String.format("Order %s is %s, can not be canceled!", order.getOrderId(), order.getStatus()));
      return OrderInfoObject.OrderStatus.CANCELED.equals(order.getStatus());
    }
    LOGGER.debug(String.format("Cancelling %s order %s for %s by %s on %s",
        order.getDirection(), order.getOrderId(), order.getAmount(), order.getPrice(), order.getSymbol()));
    if (testOrders) {
      LOGGER.debug(String.format("Test order on %s cancelled: amount - %s price - %s",
          order.getSymbol(), order.getAmount(), order.getPrice()));
      order.setStatus(OrderInfoObject.OrderStatus.CANCELED);
      return true;
    }
    try {
      return Utils.executeInFewAttempts(() ->
          ((BinanceSignedClient) apiClient).cancelOrder(order.getSymbol(), null, order.getOrderId()) != null,
          3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to cancel %s order %s for %s by %s on %s",
          order.getDirection(), order.getOrderId(), order.getAmount(), order.getPrice(), order.getSymbol()), e);
      return false;
    }
  }
  private static Order findExistingOrder(List<Order> orders, String symbol,
                                         OrderSide orderSide, PositionSide positionSide, OrderType orderType) {
    if (orders == null)
      return null;
    for (Order order : orders) {
      if (symbol.equals(order.getSymbol()) && orderSide.name().equals(order.getSide()) &&
          positionSide.name().equals(order.getPositionSide()) && orderType.name().equals(order.getType()))
        return order;
    }
    return null;
  }

  //TODO проработать механизм update'а. Хреново, что он принимает ордер одного типа, а возвращает другого

  protected Order updateOrder(BinanceSignedClient apiClient, OrderInfoObject order) {
    if (order == null) {
      LOGGER.error("Trying to update null order");
      return null;
    }
    try {
      return Utils.executeInFewAttempts(() ->
          apiClient.queryOrder(order.getSymbol(), null, order.getOrderId()), 3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error(String.format("Update order %s failed", order.getOrderId()));
      throw new SystemException(e);
    }
  }

  private static long getDefaultAttemptPause() {
    return 1000L;
  }

  public static Order createTestOrder(String pair, String quantity, String price, OrderSide orderSide,
                                      PositionSide positionSide, OrderType orderType) {
    return createTestOrder(pair, quantity, price, orderSide, positionSide, orderType, OrderStatus.FILLED);
  }

  @NotNull
  public static Order createTestOrder(String pair, String quantity, String price, OrderSide orderSide,
                                      PositionSide positionSide, OrderType orderType, OrderStatus orderStatus) {
    Order order = new Order();
    order.setSymbol(pair);
    order.setOrigQty(new BigDecimal(quantity));
    order.setExecutedQty(new BigDecimal(quantity));
    order.setPrice(new BigDecimal(price));
    order.setAvgPrice(new BigDecimal(price));
    order.setStopPrice(new BigDecimal(price));
    order.setSide(orderSide.name());
    order.setPositionSide(positionSide.name());
    order.setType(orderType.name());
    order.setStatus(orderStatus.name());
    order.setUpdateTime(DateUtils.currentTimeMillis());
    order.setClientOrderId(generateTestOrderId());
    LOGGER.debug(String.format("Test order on %s created: amount - %s price - %s",
        order.getSymbol(), order.getOrigQty(), order.getPrice()));
    return order;
  }

  private static String generateTestOrderId() {
    /*byte[] array = new byte[24]; // length is limited by 24
    new Random().nextBytes(array);
    return new String(array, Utils.DEFAULT_CHARSET);*/
    return "test" + UUID.randomUUID().toString().replaceAll("-", "");
  }

  @NotNull
  protected static PositionSide getClosingPositionSide(boolean buy) {
    return buy ? PositionSide.LONG : PositionSide.SHORT;
  }

  @NotNull
  protected static OrderSide getClosingOrderSide(boolean buy) {
    return buy ? OrderSide.SELL : OrderSide.BUY;
  }

  private List<Order> openOrders = null;
  private long openOrdersTs = 0L;
  protected List<Order> getOpenOrders(BinanceSignedClient apiClient) throws Exception {
    if (testOrders)
      return null;
    //todo кеширование должно быть разделено для разных клиентов if (openOrders == null || isOpenOrdersCacheExpired()) {
    openOrders = apiClient.getOpenOrders();
    openOrdersTs = DateUtils.currentTimeMillis();
    LOGGER.debug("Open orders loaded count - " + openOrders.size());
    for (Order o : openOrders) {
      LOGGER.trace(o.toString());
    }
    //}
    return openOrders;
  }

  private boolean isOpenOrdersCacheExpired() {
    return DateUtils.currentTimeMillis() - openOrdersTs > OPEN_ORDERS_CACHE_LIVE_TIME;
  }

  /*@Override
  public BinanceOrdersProcessor.PositionContainer createPositionContainer(Object customOrder) {
    BinanceOrdersProcessor.PositionContainer result = new BinanceOrdersProcessor.PositionContainer();
    result.setPositionInfo(preparePositionInfo(customOrder));
    result.addOrder(convertOrder(customOrder, result.getPositionInfo().getPositionId()));
    return result;
  }*/

  private static String formatDecimal(double value) {
    return Utils.formatFloatValue((float) value, 4);
  }

  public static class PositionContainer extends FuturesPositionContainer {
    private final AtomicInteger safetyOrdersTriggered = new AtomicInteger(0);

    public PositionContainer() {

    }

    public PositionContainer(PositionInfo position) {
      this.position = (PositionInfoObject) position;
    }

    @Deprecated(/*используется только в тестах*/)
    public PositionContainer(Object baseOrder) {
      addOrder(getOrdersProcessor().convertOrder(baseOrder, null));
    }

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

    protected BinanceOrdersProcessor getOrdersProcessor() {
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
    public OrderInfoObject closeDeal(AbstractSignedClient apiAccess) {
      if (!(apiAccess instanceof BinanceSignedClient)) {
        LOGGER.warn("Needed API connector aren't provided");
        return null;
      }
      Order result = null;
      try {
        result = getOrdersProcessor().closeDeal((BinanceSignedClient) apiAccess,
            getPositionInfo().getSymbol(), getExecutedAmount(),
            Consts.BUY.equals(getPositionInfo().getDirection()));
      } catch (Exception e) {
        LOGGER.error(String.format("Failed to close %s deal for %s %s by market",
            getPositionInfo().getDirection(), getExecutedAmount(), getPositionInfo().getSymbol()), e);
        return null;
      }
      //TP и SL ордера отменяются автоматом
      //getOrdersProcessor().cancelOrder((SignedClient) apiAccess, takeProfitOrder);
      //getOrdersProcessor().cancelOrder((SignedClient) apiAccess, stopLossOrder);
      return getOrdersProcessor().convertOrder(result, getPositionInfo().getPositionId());
    }

    @Override
    public int update(AbstractSignedClient apiAccess) {
      int result = 0;
      if (!(apiAccess instanceof BinanceSignedClient))
        return result;
      LOGGER.debug(String.format("Updating position container with id = %s", position.getPositionId()));
      if (getStopLossOrder() != null) {
        Order slUpdated = getOrdersProcessor().updateOrder((BinanceSignedClient) apiAccess, getStopLossOrder());
        if (slUpdated != null) {
          stopLossOrder = getOrdersProcessor().convertOrder(slUpdated, getPositionInfo().getPositionId());
          result++;
        }
      }
      if (getTakeProfitOrder() != null) {
        Order tpUpdated = getOrdersProcessor().updateOrder((BinanceSignedClient) apiAccess, getTakeProfitOrder());
        if (tpUpdated != null) {
          takeProfitOrder = getOrdersProcessor().convertOrder(tpUpdated, getPositionInfo().getPositionId());
          result++;
        }
      }

      for (OrderInfoObject order : new ArrayList<>(orders)) {
        OrderInfoObject safetyUpdated = getOrdersProcessor().convertOrder(
            getOrdersProcessor().updateOrder((BinanceSignedClient) apiAccess, order), getPositionInfo().getPositionId());
        if (safetyUpdated != null) {
          orders.remove(order);
          orders.add(safetyUpdated);
          result++;
        }
      }
      updatePositionStatus();

      return result;
    }

    @Override
    public int cancel(AbstractSignedClient apiAccess) {
      int result = 0;
      if (!(apiAccess instanceof BinanceSignedClient))
        return result;

      if (position != null)
        LOGGER.debug(String.format("Updating position container with id = %s", position.getPositionId()));
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
    public boolean cancelSafetyOrders(AbstractSignedClient apiAccess) {
      if (!(apiAccess instanceof BinanceSignedClient))
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
    public Object rearrangeTakeProfit(AbstractSignedClient apiAccess, @Nullable BigDecimal newTp) {
      if (!(apiAccess instanceof BinanceSignedClient))
        return null;
      LOGGER.debug("rearrangeTakeProfit, new value = " + newTp);
      /*if (newTp == null) {
        if (parameters.containsKey(PARAM_TAKE_PROFIT_PERCENT)) {
          Object tpPercent = parameters.get(PARAM_TAKE_PROFIT_PERCENT);
          if (tpPercent instanceof Float) {
            BigDecimal avg = getAvgPrice();
            newTp = avg.multiply(BigDecimal.valueOf(1 + (float) tpPercent));
          }
        }
      }*/
      if (newTp == null) {
        LOGGER.warn(String.format("Can't rearrange TP order %s: no value provided",
            getTakeProfitOrder().getOrderId()));
        return null;
      }
      //существующий TP отменится в placeTakeProfit
      /*if (getTakeProfitOrder() != null) {
        Order tp = getOrdersProcessor().cancelOrder((SignedClient) apiAccess, getTakeProfitOrder());
        if (tp == null)
          return null;
      }*/
      try {
        Order newTPOrder = getOrdersProcessor().placeTakeProfit((BinanceSignedClient) apiAccess,
            getOrdersProcessor().getOpenOrders((BinanceSignedClient) apiAccess),
            BinanceFuturesApiProvider.getSymbolInfo(position.getSymbol()), this, newTp.floatValue());
        if (newTPOrder == null)
          throw new NullPointerException("New TP order is null");
        if (takeProfitOrder != null) {
          try {
            this.outdatedOrders.add(getOrdersProcessor().convertOrder(
                getOrdersProcessor().updateOrder((BinanceSignedClient) apiAccess, takeProfitOrder), getPositionInfo().getPositionId()));
          } catch (Exception e) {
            LOGGER.warn(String.format("Updating outdated TP order with id = %s failed", takeProfitOrder.getOrderId()));
          }
        }
        this.takeProfitOrder = getOrdersProcessor().convertOrder(newTPOrder, getPositionInfo().getPositionId());
        /*LOGGER.debug(String.format("Set a %s take profit order with id %s for base order %s",
            newTPOrder.getSide(), newTPOrder.getClientOrderId(), baseOrder.getClientOrderId()));*/
        return newTPOrder;
      } catch (Exception e) {
        LOGGER.error(String.format("Failed %s TP order for %s %s by %s",
            position.getDirection(), position.getAmount(), position.getSymbol(), newTp), e);
        return null;
      }
    }

    @Override
    public Object rearrangeStopLoss(AbstractSignedClient apiAccess, BigDecimal newSl) {
      if (!(apiAccess instanceof BinanceSignedClient))
        return null;
      LOGGER.debug("rearrangeStopLoss, new value = " + newSl);
      if (newSl == null) {
        LOGGER.warn(String.format("Can't rearrange SL order %s: no value provided",
            getStopLossOrder().getOrderId()));
        return null;
      }
      //существующий SL отменится в placeStopLoss
      /*if (getStopLossOrder() != null) {
        Order sl = getOrdersProcessor().cancelOrder((SignedClient) apiAccess, getStopLossOrder());
        if (sl == null)
          return null;
      }*/
      try {
        Order newSlOrder = getOrdersProcessor().placeStopLoss((BinanceSignedClient) apiAccess,
            getOrdersProcessor().getOpenOrders((BinanceSignedClient) apiAccess),
            BinanceFuturesApiProvider.getSymbolInfo(position.getSymbol()), this, newSl.floatValue());
        if (newSlOrder == null)
          throw new NullPointerException("New SL order is null");
        if (stopLossOrder != null) {
          try {
            this.outdatedOrders.add(getOrdersProcessor().convertOrder(
                getOrdersProcessor().updateOrder((BinanceSignedClient) apiAccess, stopLossOrder), getPositionInfo().getPositionId()));
          } catch (Exception e) {
            LOGGER.warn(String.format("Updating outdated SL order with id = %s failed", stopLossOrder.getOrderId()));
          }
        }
        this.stopLossOrder = getOrdersProcessor().convertOrder(newSlOrder, getPositionInfo().getPositionId());
        /*LOGGER.debug(String.format("Set a %s stop loss order with id %s for base order %s",
            newSlOrder.getSide(), newSlOrder.getClientOrderId(), baseOrder.getClientOrderId()));*/
        return newSlOrder;
      } catch (Exception e) {
        LOGGER.error(String.format("Failed %s SL order for %s %s by %s",
            position.getDirection(), position.getAmount(), position.getSymbol(), newSl), e);
        return null;
      }
    }

    @Override
    public String toString() {
      return toString(null);
    }
  }
}