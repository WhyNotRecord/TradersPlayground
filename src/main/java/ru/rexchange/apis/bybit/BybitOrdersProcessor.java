package ru.rexchange.apis.bybit;

import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.OrderStatus;
import com.bybit.api.client.domain.trade.PositionIdx;
import com.bybit.api.client.domain.trade.Side;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.apis.binance.BinanceOrdersProcessor;
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
import ru.rexchange.trading.trader.BybitSignedClient;
import ru.rexchange.trading.trader.futures.BybitOrder;
import ru.rexchange.trading.trader.futures.BybitSymbolInfo;
import ru.rexchange.trading.trader.futures.FuturesPositionContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BybitOrdersProcessor extends AbstractOrdersProcessor<BybitOrder, BybitSignedClient> {
  protected static Logger LOGGER = LoggerFactory.getLogger(BybitOrdersProcessor.class);
  private static BybitOrdersProcessor instance = null;
  private static final long OPEN_ORDERS_CACHE_LIVE_TIME = 60 * 1000L;
  private boolean testOrders = false;

  protected BybitOrdersProcessor(boolean testOrders) {
    this.testOrders = testOrders;
  }

  public static BybitOrdersProcessor getInstance(boolean testOrders) {
    if (instance == null) {
      instance = new BybitOrdersProcessor(testOrders);
    }
    return instance;
  }

  @Override
  public OrderInfoObject limitOrder(BybitSignedClient aClient, float price, String pair,
                                    double amount, boolean buy, Integer leverage) throws UserException {
    LOGGER.debug("Placing a {} limit order for {}", buy ? "buy" : "sell", pair);
    BybitSymbolInfo symbolInfo = BybitFuturesApiProvider.getInstrumentInfo(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, amount);
    String priceStr = checkAndFitPrice(symbolInfo, price, 0);

    try {
      BybitOrder order = buy ?
          openBuy(aClient, true, pair, convertedAmount, priceStr) :
          openSell(aClient, true, pair, convertedAmount, priceStr);
      LOGGER.debug("Posted a limit {} order with id {} for {} {} by {}",
          order.getSide(), order.getOrderId(), convertedAmount, pair, priceStr);
      return convertOrder(order, null);
    } catch (Exception e) {
      LOGGER.error("Place {} order failed: {} {}", (buy ? "buy" : "sell"), convertedAmount, pair);
      throw new SystemException(e);
    }
  }


  @Override
  public PositionContainer createEmptyPositionContainer() {
    return new PositionContainer();
  }

  @Override
  public OrderInfoObject queryOrder(BybitSignedClient client, String symbol, String orderId) throws UserException {
    try {
      return convertOrder(client.queryOrder(symbol, orderId), null);
    } catch (Exception e) {
      throw new UserException("Error occurred while querying order " + orderId, e);
    }
  }

  @Override
  public OrderInfoObject convertOrder(BybitOrder customOrder, String positionId) {
    if (customOrder == null)
      return null;
    OrderInfoObject result = new OrderInfoObject();
    fillOrderObject(result, customOrder, positionId);

    return result;
  }

  public static OrderInfoObject fillOrderObject(OrderInfoObject orderObj, BybitOrder order, String parentId) {
    orderObj.setOrderId(order.getOrderId());
    orderObj.setExternalId(order.getOrderId());
    orderObj.setDirection(order.getSide());

    orderObj.setType(defineType(order));
    if (orderObj.getType() == null)
      orderObj.setType(order.getType());
    //orderObj.setPositionSide(definePositionSide(order.getPositionIdx()));
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

  private static String defineType(BybitOrder order) {

    if (TradeOrderType.MARKET.name().equals(order.getType()))
      return OrderInfoObject.Type.CLOSE_ORDER;
        //&& getClosingOrderSide(buy), getClosingPositionSide(buy),
    return null;
  }

  private static String definePositionSide(PositionIdx positionIdx) {
    return PositionIdx.HEDGE_MODE_BUY.equals(positionIdx) ? Consts.BUY : Consts.SELL;
  }

  @Override
  public PositionContainer placeOrder(BybitSignedClient aClient, AbstractPositionContainer<BybitSignedClient> openPosition,
                                                             boolean limit, float price, String pair, double amount, Integer leverage,
                                                             boolean buy, @Nullable Float stopLoss, @Nullable Float takeProfit) throws UserException {
    LOGGER.debug("Placing a {} limit order for {}", buy ? "buy" : "sell", pair);
    if (leverage != null && !testOrders) {
        setLeverageSafe(aClient, pair, leverage);
    }
    BybitSymbolInfo symbolInfo = BybitFuturesApiProvider.getInstrumentInfo(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, amount);
    String priceStr = checkAndFitPrice(symbolInfo, price,
        buy ? getLimitOrderShiftInTicks() : -getLimitOrderShiftInTicks());

    try {
      BybitOrder order = buy ?
          openBuy(aClient, limit, pair, convertedAmount, priceStr) :
          openSell(aClient, limit, pair, convertedAmount, priceStr);
      LOGGER.debug("Placed a {} order with id {} for {} {} by {}",
          order.getSide(), order.getOrderId(), convertedAmount, pair, priceStr);
      if (!OrderStatus.FILLED.name().equals(order.getStatus())) {
        LOGGER.info("Order is {}, not {}!", order.getStatus(), OrderStatus.FILLED.name());
      }
      PositionContainer result;

      if (openPosition == null) {
        result = (PositionContainer) (createPositionContainer(convertOrder(order, null)));
      } else {
        result = (PositionContainer) openPosition;
        openPosition.addOrder(convertOrder(order, openPosition.getPositionInfo().getPositionId()));
      }
      BybitOrder takeProfitOrder, stopLossOrder = null;
      List<BybitOrder> orders = getOpenOrders(aClient, pair);
      if (stopLoss != null && !Float.isNaN(stopLoss)) {
        stopLossOrder = placeStopLoss(aClient, orders, symbolInfo, result, stopLoss);
        if (stopLossOrder == null && cancelOrder(aClient, order.getOrderId())) {
          return null;
        }
        result.setStopLossOrder(convertOrder(stopLossOrder, result.getPositionInfo().getPositionId()));
      }
      if (takeProfit != null && !Float.isNaN(takeProfit)) {
        takeProfitOrder = placeTakeProfit(aClient, orders, symbolInfo, result, takeProfit);
        if (takeProfitOrder == null && cancelOrder(aClient, order.getOrderId())) {
          if (stopLossOrder != null)
            cancelOrder(aClient, stopLossOrder.getOrderId());
          return null;
        }
        result.setTakeProfitOrder(convertOrder(takeProfitOrder, result.getPositionInfo().getPositionId()));
      }

      return result;
    } catch (Exception e) {
      LOGGER.error("Place {} order failed: {} {}", (buy ? "buy" : "sell"), convertedAmount, pair);
      throw new SystemException(e);
    }
  }

  @Override
  public AbstractPositionContainer<BybitSignedClient> placeMarketOrder(BybitSignedClient aClient, AbstractPositionContainer<BybitSignedClient> openPosition,
                                            String pair, double amount, Integer leverage, boolean buy) throws UserException {
    LOGGER.debug("Placing a {} market order for {}", buy ? "buy" : "sell", pair);
    if (leverage != null && !testOrders) {
        setLeverageSafe(aClient, pair, leverage);
    }
    BybitSymbolInfo symbolInfo = BybitFuturesApiProvider.getInstrumentInfo(pair);

    try {
      float lastPrice = getLastPrice(pair);
      String convertedAmount = checkAndFitAmount(symbolInfo, lastPrice, amount);
      /*String priceStr = checkAndFitPrice(symbolInfo, price,
          buy ? getLimitOrderShiftInTicks() : -getLimitOrderShiftInTicks());*/

      BybitOrder order = buy ?
          openBuy(aClient, false, pair, convertedAmount, null) :
          openSell(aClient, false, pair, convertedAmount, null);
      LOGGER.debug("Placed a {} order with id {} for {} {} by {}",
          order.getSide(), order.getOrderId(), convertedAmount, pair, lastPrice);
      if (!OrderStatus.FILLED.name().equals(order.getStatus())) {
        LOGGER.info("Order is {}, not {}!", order.getStatus(), OrderStatus.FILLED.name());
      }
      if (openPosition == null)
        return createPositionContainer(convertOrder(order, null));
      else {
        openPosition.addOrder(convertOrder(order, null));
        return openPosition;
      }
    } catch (Exception e) {
      LOGGER.error("Place {} order on {} failed", (buy ? "buy" : "sell"), pair);
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
  protected String checkAndFitAmount(BybitSymbolInfo symbolInfo, float price, double amount) throws UserException {
    String convertedAmount = String.valueOf(amount);
    BigDecimal originalDecimal = new BigDecimal(convertedAmount);
    if (convertedAmount.length() > 6)
      convertedAmount = convertedAmount.substring(0, 6);
    if (symbolInfo != null) {
      //List<Map<String, String>> filters = symbolInfo.getFilters();//TODO magic consts
      String minQty = getFilterValue(symbolInfo, "lotSizeFilter", "minOrderQty");
      String minNotational = getFilterValue(symbolInfo, "lotSizeFilter", "minNotionalValue");//минимальная сумма с учётом плеча

      if (minQty != null) {
        double minQtyDouble = Double.parseDouble(minQty);
        //Check LOT_SIZE to make sure amount is not too small
        if (amount < minQtyDouble) {
          throw new UserException("Amount (%s) smaller than min LOT_SIZE (%s) for %s, could not open trade!",
              amount, minQty, symbolInfo.getSymbol());
        }

        //Convert amount to an integer multiple of LOT_SIZE and convert to asset precision
        LOGGER.trace("Converting from double trade amount {} LOT_SIZE - {}", originalDecimal, minQty);
        //TODO проверить эти формулы и упростить, если возможно
        convertedAmount = new BigDecimal(minQty).multiply(new BigDecimal(amount / minQtyDouble)).
            setScale(new BigDecimal(minQty).scale(), RoundingMode.HALF_DOWN).
            //может вставить E stripTrailingZeros().
            toString();
        LOGGER.trace("Converted to {}", convertedAmount);

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

  protected String checkAndFitPrice(BybitSymbolInfo symbolInfo, float price, int shift) {
    String preparedPrice = String.valueOf(price);
    LOGGER.trace("Preparing order price value: {}", price);
    if (symbolInfo != null) {
      //List<Map<String, String>> filters = symbolInfo.getFilters();//TODO magic consts
      String tickSize = getFilterValue(symbolInfo, "priceFilter", "tickSize");
      preparedPrice = checkByRange(getFilterValue(symbolInfo, "priceFilter", "minPrice"),
          getFilterValue(symbolInfo, "priceFilter", "maxPrice"), price, symbolInfo.getSymbol());
      if (tickSize != null)
        preparedPrice = roundAndShiftPriceByTickSize(preparedPrice, tickSize, shift);
    } else {
      if (preparedPrice.length() > 7)
        preparedPrice = preparedPrice.substring(0, 7);
    }
    LOGGER.trace("Fitted price value is {}", preparedPrice);
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


    protected String checkByRange(String minValue, String maxValue, float price, String symbol) {
    if (minValue == null || maxValue == null) {
      LOGGER.warn("Can not check price value {}. No range provided", price);
      return String.valueOf(price);
    }
    Float fMin = Float.valueOf(minValue),
        fMax = Float.valueOf(maxValue);
    if (price > fMin && price < fMax)
      return String.valueOf(price);
    if (price < fMin) {
      LOGGER.warn("Order price is too small - {}. Returning minimum range value for {}: {}", price, symbol, fMin);
      return String.valueOf(fMin);
    }
    if (price > fMax) {
      LOGGER.warn("Order price is too big - {}. Returning maximum range value for {}: {}", price, symbol, fMax);
      return String.valueOf(fMax);
    }
    return String.valueOf(price);
  }

  private static String getFilterValue(BybitSymbolInfo info, String filterType, String field) {
    if (info.contains(filterType))
      return info.getMap(filterType).get(field).toString();
    /*for (Map<String, String> filter : filters) {
      if (filterType.equals(filter.get("filterType")))
        return filter.get(field);
    }*/
    return null;
  }

  public static boolean setLeverage(BybitSignedClient apiClient, String symbol, Integer leverage) throws Exception {
    LOGGER.debug("Setting leverage for pair {} to {}", symbol, leverage);
    return apiClient.setLeverage(symbol, leverage);
  }

  public static boolean setLeverageSafe(BybitSignedClient apiClient, String pair, Integer leverage) {
    try {
      return setLeverage(apiClient, pair, leverage);
    } catch (Exception e) {
      LOGGER.error("Unsuccessful leverage change for pair {}", pair, e);
      return false;
    }
  }

  private BybitOrder openBuy(BybitSignedClient client, boolean limit, String pair, String quantity, String price) throws Exception {
    if (limit) {
      return client.placeOrder(pair, Side.BUY, PositionIdx.HEDGE_MODE_BUY, TradeOrderType.LIMIT,
          quantity, price, null, null);
    } else {
      return client.placeOrder(pair, Side.BUY, PositionIdx.HEDGE_MODE_BUY, TradeOrderType.MARKET,
          quantity, price, null, null);
    }
  }

  private BybitOrder openSell(BybitSignedClient client, boolean limit, String pair, String quantity, String price) throws Exception {
    if (limit) {
      return client.placeOrder(pair, Side.SELL, PositionIdx.HEDGE_MODE_SELL, TradeOrderType.LIMIT,
          quantity, price, null, null);
    } else {
      return client.placeOrder(pair, Side.SELL, PositionIdx.HEDGE_MODE_SELL, TradeOrderType.MARKET,
          quantity, price, null, null);
    }
  }

  protected BybitOrder closeDeal(BybitSignedClient client, String pair, BigDecimal quantity, boolean buy) throws Exception {
    BybitSymbolInfo symbolInfo = BybitFuturesApiProvider.getInstrumentInfo(pair);
    float price = getLastPrice(pair);
    String convertedAmount = checkAndFitAmount(symbolInfo, price, quantity.doubleValue());
    return client.placeOrder(pair, getClosingOrderSide(buy), getClosingPositionSide(buy),
        TradeOrderType.MARKET, null, convertedAmount, true, null);
  }

  protected BybitOrder placeTakeProfit(BybitSignedClient apiClient, List<BybitOrder> orders, BybitSymbolInfo symbolInfo,
                                  PositionContainer position, float takeProfitValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = Consts.BUY.equals(positionInfo.getDirection());
    String priceStr = String.valueOf(takeProfitValue);
    //todo несколько попыток
    try {
      //Отменяем существующий take-profit при наличии
      if (checkForExistingTakeProfit(apiClient, orders, buy, positionInfo.getSymbol()) != null) {
        LOGGER.debug("Trying to recreate TP order at new price {}", takeProfitValue);
      }
      if (takeProfitValue == 0) {
        LOGGER.info("TP price equals zero, leaving position without take-profit");
        return null;
      }
      priceStr = checkAndFitPrice(symbolInfo, takeProfitValue, 0);
      BybitOrder order = apiClient.placeOrder(positionInfo.getSymbol(), getClosingOrderSide(buy), getClosingPositionSide(buy),
            TradeOrderType.MARKET, null, priceStr, true, buy ? 1 : 2);
      LOGGER.debug("Set a {} take profit order with id {} for position {}",
          order.getSide(), order.getOrderId(), positionInfo.getPositionId());
      return order;
    } catch (Exception e) {
      LOGGER.error("Failed {} TP order for {} {} by {}",
          positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), priceStr, e);
      return null;
    }
  }

  private Float checkForExistingTakeProfit(BybitSignedClient apiClient, List<BybitOrder> openOrders,
                                           boolean buy, String symbol) {
    BybitOrder existingTakeProfit = findExistingOrder(openOrders,
        symbol, getClosingOrderSide(buy), getClosingPositionSide(buy), TradeOrderType.MARKET);
    if (existingTakeProfit != null) {
      float stopPrice = existingTakeProfit.getStopPrice().floatValue();
      LOGGER.debug("Existing TP at {} found", stopPrice);
      if (!cancelOrder(apiClient, existingTakeProfit.getOrderId()))
        throw new SystemException("Unsuccessful TP order cancellation " + existingTakeProfit.getOrderId());
      return stopPrice;
    }
    return null;
  }

  protected BybitOrder placeStopLoss(BybitSignedClient apiClient, List<BybitOrder> orders, BybitSymbolInfo symbolInfo,
                                PositionContainer position, float stopLossValue) {
    PositionInfoObject positionInfo = position.getPositionInfo();
    boolean buy = Consts.BUY.equals(positionInfo.getDirection());
    //todo несколько попыток
    String priceStr = String.valueOf(stopLossValue);
    try {
      //Отменяем существующий stop-loss при наличии
      if (checkForExistingStopLoss(apiClient, orders, buy, positionInfo.getSymbol()) != null) {
        LOGGER.debug("Trying to recreate SL order at new price {}", stopLossValue);
      }
      priceStr = checkAndFitPrice(symbolInfo, stopLossValue, 0);
      BybitOrder order;
        order = apiClient.placeOrder(positionInfo.getSymbol(), getClosingOrderSide(buy), getClosingPositionSide(buy),
            TradeOrderType.MARKET, null, priceStr, true, buy ? 2 : 1);
      LOGGER.debug("Set a {} stop loss order with id {} for base order {}",
          order.getSide(), order.getOrderId(), positionInfo.getPositionId());
      return order;
    } catch (Exception e) {
      LOGGER.error("Failed {} SL order for {} {} by {}",
          positionInfo.getDirection(), positionInfo.getAmount(), positionInfo.getSymbol(), priceStr, e);
      return null;
    }
  }

  private Float checkForExistingStopLoss(BybitSignedClient apiClient, List<BybitOrder> openOrders,
                                         boolean buy, String symbol) {
    BybitOrder existingStopLoss = findExistingOrder(openOrders,
        symbol, getClosingOrderSide(buy), getClosingPositionSide(buy), TradeOrderType.MARKET);
    if (existingStopLoss != null) {//логика корректировки стоп-лосса
      float stopPrice = existingStopLoss.getStopPrice().floatValue();
      LOGGER.debug("Existing SL at {} found", stopPrice);
      if (!cancelOrder(apiClient, existingStopLoss.getOrderId()))
        throw new SystemException("Unsuccessful SL order cancellation " + existingStopLoss.getOrderId());
      return stopPrice;
    }
    return null;
  }

  @Override
  public float getLastPrice(String symbol) throws Exception {
    Float price = Utils.getCachedObject("bybit.price." + symbol, 3000L,
        () -> BybitFuturesApiProvider.getLastPrice(symbol));
    if (price == null)
      throw new SystemException("Last price request for symbol %s failed", symbol);
    return price;
  }

  @Override
  public boolean cancelOrder(BybitSignedClient apiClient, String orderId) {
    if (orderId == null) {
      LOGGER.warn("No orderId provided");
      return false;
    }
    LOGGER.debug("Cancelling order {}", orderId);
    if (testOrders) {
      /*LOGGER.debug("Test order on {} cancelled: amount - {} price - {}",
          order.getSymbol(), order.getAmount(), order.getPrice());
      order.setStatus(OrderInfoObject.OrderStatus.CANCELED);*/
      //todo обновлять статус выше по стеку
      return true;
    }
    try {
      return Utils.executeInFewAttempts(() -> apiClient.cancelOrder(null, orderId),
          3, getDefaultAttemptPause());
    } catch (Exception e) {
      LOGGER.error("Failed to cancelorder {}", orderId, e);
      return false;
    }
  }
  
  private static BybitOrder findExistingOrder(List<BybitOrder> orders, String symbol,
                                         Side orderSide, PositionIdx positionSide, TradeOrderType orderType) {
    if (orders == null)
      return null;
    for (BybitOrder order : orders) {
      if (symbol.equals(order.getSymbol()) && orderSide.name().equals(order.getSide()) &&
          positionSide.equals(order.getPositionIdx()) && orderType.name().equals(order.getType()))
        return order;
    }
    return null;
  }
  @Override
  public BybitOrder updateOrder(BybitSignedClient apiAccess, OrderInfoObject order) throws SocketException, UnknownHostException {
    if (order == null) {
      LOGGER.error("Trying to update null order");
      return null;
    }
    try {
      return Utils.executeInFewAttempts(() ->
          apiAccess.queryOrder(order.getSymbol(), order.getOrderId()), 3, getDefaultAttemptPause());
    } catch (SocketException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("Update order {} failed", order.getOrderId());
      //todo process "Order does not exist", remove from position
      throw new SystemException(e);
    }
  }

  protected static PositionIdx getClosingPositionSide(boolean buy) {
    return buy ? PositionIdx.HEDGE_MODE_BUY : PositionIdx.HEDGE_MODE_SELL;
  }

  protected static Side getClosingOrderSide(boolean buy) {
    return buy ? Side.SELL : Side.BUY;
  }

  private List<BybitOrder> openOrders = null;
  private long openOrdersTs = 0L;
  protected List<BybitOrder> getOpenOrders(BybitSignedClient apiClient, String symbol) throws Exception {
    if (testOrders)
      return null;
    //todo кеширование должно быть разделено для разных клиентов if (openOrders == null || isOpenOrdersCacheExpired()) {
    openOrders = apiClient.getOrders(true, symbol);
    openOrdersTs = DateUtils.currentTimeMillis();
    LOGGER.debug("Open orders loaded count - {}", openOrders.size());
    for (BybitOrder o : openOrders) {
      LOGGER.trace(o.toString());
    }
    //}
    return openOrders;
  }

  private boolean isOpenOrdersCacheExpired() {
    return DateUtils.currentTimeMillis() - openOrdersTs > OPEN_ORDERS_CACHE_LIVE_TIME;
  }

  private static String formatDecimal(double value) {
    return Utils.formatFloatValue((float) value, 4);
  }

  public static class PositionContainer extends FuturesPositionContainer<BybitSignedClient> {
    private final AtomicInteger safetyOrdersTriggered = new AtomicInteger(0);

    public PositionContainer() {

    }

    public PositionContainer(PositionInfo position) {
      this.position = (PositionInfoObject) position;
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

    @Override
    protected BybitOrdersProcessor getOrdersProcessor() {
      return getInstance(false);
    }

    //todo многое можно вынести в предка
	
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
    public OrderInfoObject closeDeal(BybitSignedClient apiAccess) {
      if (apiAccess == null) {
        LOGGER.warn("Needed API connector aren't provided");
        return null;
      }
      BybitOrder result = null;
      try {
        result = getOrdersProcessor().closeDeal(apiAccess,
            getPositionInfo().getSymbol(), getExecutedAmount(),
            Consts.BUY.equals(getPositionInfo().getDirection()));
      } catch (Exception e) {
        LOGGER.error("Failed to close {} deal for {} {} by market",
            getPositionInfo().getDirection(), getExecutedAmount(), getPositionInfo().getSymbol(), e);
        return null;
      }
      //TP и SL ордера отменяются автоматом
      //getOrdersProcessor().cancelOrder((SignedClient) apiAccess, takeProfitOrder);
      //getOrdersProcessor().cancelOrder((SignedClient) apiAccess, stopLossOrder);
      return getOrdersProcessor().convertOrder(result, getPositionInfo().getPositionId());
    }

    @Override
    public OrderInfoObject closePartially(BybitSignedClient apiAccess, BigDecimal amount) {
      if (apiAccess == null) {
        LOGGER.warn("Expected API connector aren't provided");
        return null;
      }
      BybitOrder result = null;
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
	
    @Override
    public int cancel(BybitSignedClient apiAccess) {
      int result = 0;
      if (apiAccess == null)
        return result;

      if (position != null)
        LOGGER.debug("Cancelling position container with id = {}", position.getPositionId());

      if (getStopLossOrder() != null) {
        if (getOrdersProcessor().cancelOrder(apiAccess, getStopLossOrder().getOrderId()))
          result++;
      }
      if (getTakeProfitOrder() != null) {
        if (getOrdersProcessor().cancelOrder(apiAccess, getTakeProfitOrder().getOrderId()))
          result++;
      }

      for (OrderInfoObject order : orders) {
        if (order != null) {
          if (getOrdersProcessor().cancelOrder(apiAccess, order.getOrderId()))
            result++;
        }
      }

      return result;
    }

    @Override
    public boolean cancelSafetyOrders(BybitSignedClient apiAccess) {
      if (apiAccess == null)
        return false;
      boolean result = true;
      for (OrderInfoObject order : new ArrayList<>(orders)) {
        if (!OrderInfoObject.OrderStatus.FILLED.equals(order.getStatus())) {
          boolean safetyCancelled = getOrdersProcessor().cancelOrder(apiAccess, order.getOrderId());
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
    public boolean rearrangeTakeProfit(BybitSignedClient apiAccess, BigDecimal newTp) {
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
        BybitOrder newTPOrder = getOrdersProcessor().placeTakeProfit(apiAccess,
            getOrdersProcessor().getOpenOrders(apiAccess, position.getSymbol()),
            BybitFuturesApiProvider.getInstrumentInfo(position.getSymbol()), this, newTp.floatValue());
        if (!removeOnly && newTPOrder == null)
          throw new NullPointerException("New TP order is null");
        if (takeProfitOrder != null) {
          try {
            // почему удалённый ТП не обновляет статус в БД?
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
        LOGGER.error("Failed {} TP order for {} {} by {}",
            position.getDirection(), position.getAmount(), position.getSymbol(), newTp, e);
        return false;
      }
    }

    @Override
    public Object rearrangeStopLoss(BybitSignedClient apiAccess, BigDecimal newSl) {
      if (apiAccess == null)
        return null;
      LOGGER.debug("rearrangeStopLoss, new value = {}", newSl);
      if (newSl == null) {
        LOGGER.warn("Can't rearrange SL order {}: no value provided", getStopLossOrder().getOrderId());
        return null;
      }
      //существующий SL отменится в placeStopLoss
      /*if (getStopLossOrder() != null) {
        Order sl = getOrdersProcessor().cancelOrder((SignedClient) apiAccess, getStopLossOrder());
        if (sl == null)
          return null;
      }*/
      try {
        BybitOrder newSlOrder = getOrdersProcessor().placeStopLoss(apiAccess,
            getOrdersProcessor().getOpenOrders(apiAccess, position.getSymbol()),
            BybitFuturesApiProvider.getInstrumentInfo(position.getSymbol()), this, newSl.floatValue());
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
