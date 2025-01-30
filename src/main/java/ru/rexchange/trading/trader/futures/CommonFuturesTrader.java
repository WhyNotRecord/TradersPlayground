package ru.rexchange.trading.trader.futures;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.Consts;
import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.exception.UserException;
import ru.rexchange.gen.*;
import ru.rexchange.tools.*;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.AbstractSignedClient;
import ru.rexchange.trading.trader.AbstractTrader;

import java.math.BigDecimal;
import java.net.SocketException;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public abstract class CommonFuturesTrader extends AbstractTrader {
  protected static Logger LOGGER = LoggerFactory.getLogger(CommonFuturesTrader.class);
  private static final Integer DEFAULT_LEVERAGE_VALUE = 2;
  public static final String STOP_LOSS_PARAM = "stop-loss";
  public static final String TAKE_PROFIT_PARAM = "take-profit";
  protected AbstractPositionContainer longPosition = null;
  protected AbstractPositionContainer shortPosition = null;
  protected boolean allowPositionIncreasing = false;
  protected float safetyOrdersMinimalDistancePercent = 0.f;
  protected Float safetyOrdersMinimalDistanceFactor = null;
  protected Integer leverage = null;
  private boolean storeOrdersInDatabase = true;
  private boolean attachToExistingPositions = false;
  private long newOrderLivePeriod = 5 * 60 * 1000L;
  private float safetyOrdersAmountAddedPercent = 0.f;
  private int maxSafetyOrdersLimit = 5;
  private float lastAppc = 0.f;

  public CommonFuturesTrader(String name, AmountDeterminant amountDeterminant, float amountValue) {
    super(name, amountDeterminant, amountValue);
  }

  public CommonFuturesTrader(TraderConfig trader) {
    super(trader);
  }

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  public AbstractPositionContainer openBuy(DealInfo info) {
    //todo отменять страховочные и переставлять stop-loss у открытого SELL-ордера
    //todo обрабатывать отфильтрованные сделки (ставить в очередь и ожидать подтверждения)
    float desiredRate = info.getDealRate();
    Float stopLoss = info.getStopLoss(), takeProfit = info.getTakeProfit();
    if (hasOpenedLongPositions() && getLongPosition().getTakeProfitOrder() != null) {
      takeProfit = getCorrectedTakeProfit(takeProfit, desiredRate, getLongPosition());
    }
    getLogger().info(prepareOpenDealMessage(Consts.BUY, desiredRate, stopLoss, takeProfit));
    double dealAmount = getDealAmount(desiredRate, true);
    AbstractPositionContainer position = null;
    try {//TODO сделать через рыночные стопы, а не обычные лимиты
      //todo продумать отложенное изменение SL/TP после фактического срабатывания safety-ордера
      position = getOrdersProcessor().placeOrder(getSignedClient(), getLongPosition(), !openByMarket, desiredRate,
          getSymbol(), dealAmount, getLeverage(), true, stopLoss, takeProfit);
      if (position != null) {
        String file = info.hasParameter(DealInfo.PARAM_FILE) ? (String) info.getParameter(DealInfo.PARAM_FILE) : null;
        notifyUser(prepareDealNotificationMessage(position.getLastOrder().getPrice(),
            dealAmount, takeProfit, stopLoss, true), file);
        LoggingUtils.logDealInfo("BUY", getName(), String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)),
            String.valueOf(info.getParameter(DealInfo.PARAM_FILTERED_DEAL)),
            baseCurrency, quotedCurrency, (Long) info.getParameter(DealInfo.PARAM_TIME),
            position.getPositionInfo().getAveragePrice(), takeProfit, stopLoss, getLogger());
        dealOpened();
        if (info.hasParameter(DealInfo.PARAM_MANUAL_LIMIT)) {
          position.setParameter(DealInfo.PARAM_MANUAL_LIMIT, Boolean.TRUE);
        }
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e,"Error occurred while opening BUY order");
      notifyUser(prepareDealFailedNotificationMessage(e.getMessage(), true));
    }
    if (position == null) {
      return null;
    }
    this.longPosition = position;
    //quotedCurrencyAmount -= getRealDealAmount(desiredRate, dealAmount);
    PositionInfoObject positionInfo = getLongPosition().getPositionInfo();
    if (positionInfo != null && positionInfo.getTraderId() == null) {
      positionInfo.setComment((String) info.getParameter(DealInfo.PARAM_STRATEGY));
      positionInfo.setTraderId(getId());
      positionInfo.setTraderName(getName());
    }

    return position;
  }

  public boolean marketBuy(Double dealAmount) {
    //TODO отменять страховочные и переставлять stop-loss у открытого SELL-ордера
    getLogger().info(String.format("Opening market BUY deal on %s", getSymbol()));
    AbstractPositionContainer position = null;
    try {
      if (dealAmount == null) {
        dealAmount = getDealAmount(getOrdersProcessor().getLastPrice(getSymbol()), true);
      }
      position = getOrdersProcessor().placeMarketOrder(getSignedClient(), getLongPosition(),
          getSymbol(), dealAmount, getLeverage(), true);
      if (position != null) {
        LoggingUtils.logDealInfo("BUY", getName(), "Manual", null,
            baseCurrency, quotedCurrency, DateUtils.currentTimeMillis(),
            position.getPositionInfo().getAveragePrice(), 0f, 0f, getLogger());
        dealOpened();
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Error occurred while opening BUY order");
      notifyUser(prepareDealFailedNotificationMessage(e.getMessage(), true));
    }
    if (position == null) {
      return false;
    }
    //quotedCurrencyAmount -= getRealDealAmount(desiredRate, dealAmount);
    longPosition = position;
    getLongPosition().getPositionInfo().setComment("Manual");
    getLongPosition().getPositionInfo().setTraderId(getId());
    getLongPosition().getPositionInfo().setTraderName(getName());

    notifyUser(prepareDealNotificationMessage(position.getLastOrder().getPrice(), dealAmount,
        position.getTakeProfitOrder() == null ? null : position.getTakeProfitOrder().getPrice(),
        position.getStopLossOrder() == null ? null : position.getStopLossOrder().getPrice(), true));

    return true;
  }

  @Override
  public AbstractPositionContainer openSell(DealInfo info) {
    //todo отменять страховочные и переставлять stop-loss у открытого BUY-ордера
    //todo обрабатывать отфильтрованные сделки (ставить в очередь и ожидать подтверждения)
    float desiredRate = info.getDealRate();
    Float stopLoss = info.getStopLoss(), takeProfit = info.getTakeProfit();
    if (hasOpenedShortPositions() && getShortPosition().getTakeProfitOrder() != null) {
      takeProfit = getCorrectedTakeProfit(takeProfit, desiredRate, getShortPosition());
    }
    getLogger().info(prepareOpenDealMessage(Consts.SELL, desiredRate, stopLoss, takeProfit));
    double dealAmount = getDealAmount(desiredRate, false);
    AbstractPositionContainer position = null;
    try {//TODO сделать через рыночные стопы, а не обычные лимиты
      //todo продумать отложенное изменение SL/TP после фактического срабатывания safety-ордера
      position = getOrdersProcessor().placeOrder(getSignedClient(), getShortPosition(), !openByMarket, desiredRate,
          getSymbol(), dealAmount, getLeverage(), false, stopLoss, takeProfit);
      if (position != null) {
        String file = info.hasParameter(DealInfo.PARAM_FILE) ? (String) info.getParameter(DealInfo.PARAM_FILE) : null;
        notifyUser(prepareDealNotificationMessage(position.getLastOrder().getPrice(),
            dealAmount, takeProfit, stopLoss, false), file);
        LoggingUtils.logDealInfo("SELL", getName(), String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)),
            String.valueOf(info.getParameter(DealInfo.PARAM_FILTERED_DEAL)),
            baseCurrency, quotedCurrency, (Long) info.getParameter(DealInfo.PARAM_TIME),
            position.getPositionInfo().getAveragePrice(), takeProfit, stopLoss, getLogger());
        dealOpened();
        if (info.hasParameter(DealInfo.PARAM_MANUAL_LIMIT)) {
          position.setParameter(DealInfo.PARAM_MANUAL_LIMIT, Boolean.TRUE);
        }
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Error occurred while opening SELL order");
      notifyUser(prepareDealFailedNotificationMessage(e.getMessage(), false));
    }

    if (position == null) {
      return null;
    }
    this.shortPosition = position;
    //quotedCurrencyAmount -= getRealDealAmount(desiredRate, dealAmount);
    PositionInfoObject positionInfo = getShortPosition().getPositionInfo();
    if (positionInfo != null && positionInfo.getTraderId() == null) {
      positionInfo.setComment((String) info.getParameter(DealInfo.PARAM_STRATEGY));
      positionInfo.setTraderId(getId());
      positionInfo.setTraderName(getName());
    }

    return position;  }

  public boolean marketSell(Double dealAmount) {
    //TODO отменять страховочные и переставлять stop-loss у открытого SELL-ордера
    getLogger().info(String.format("Opening market SELL deal on %s", getSymbol()));
    AbstractPositionContainer position = null;
    try {
      if (dealAmount == null) {
        dealAmount = getDealAmount(getOrdersProcessor().getLastPrice(getSymbol()), true);
      }
      position = getOrdersProcessor().placeMarketOrder(getSignedClient(), getShortPosition(),
          getSymbol(), dealAmount, getLeverage(), false);
      if (position != null) {
        LoggingUtils.logDealInfo("SELL", getName(), "Manual", null,
            baseCurrency, quotedCurrency, DateUtils.currentTimeMillis(),
            position.getPositionInfo().getAveragePrice(), 0f, 0f, getLogger());
        dealOpened();
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Error occurred while opening SELL order");
      notifyUser(prepareDealFailedNotificationMessage(e.getMessage(), false));
    }
    if (position == null) {
      return false;
    }
    //quotedCurrencyAmount -= getRealDealAmount(desiredRate, dealAmount);
    shortPosition = position;
    getShortPosition().getPositionInfo().setComment("Manual");
    getShortPosition().getPositionInfo().setTraderName(getName());
    getShortPosition().getPositionInfo().setTraderId(getId());
    notifyUser(prepareDealNotificationMessage(position.getLastOrder().getPrice(), dealAmount,
        position.getTakeProfitOrder() == null ? null : position.getTakeProfitOrder().getPrice(),
        position.getStopLossOrder() == null ? null : position.getStopLossOrder().getPrice(), false));

    return true;
  }

  //todo использовался для safety (разве openBuy не покрывает случай с limit-ордерами?)
  //@Override
  public OrderInfoObject limitBuy(DealInfo info) {
    float desiredRate = info.getDealRate();
    String symbol = getSymbol();
    getLogger().info(String.format("Posting limit BUY order on %s: %s", symbol, desiredRate));
    double dealAmount = getDealAmount(desiredRate, true);
    OrderInfoObject order = null;
    try {
      order = getOrdersProcessor().limitOrder(getSignedClient(), desiredRate, symbol, dealAmount, true, leverage);
      if (order != null) {
        if (getLongPosition() == null) {
          longPosition = getOrdersProcessor().createPositionContainer(order);
          getLongPosition().getPositionInfo().setComment((String) info.getParameter(DealInfo.PARAM_STRATEGY));
          getLongPosition().getPositionInfo().setTraderId(getId());
          getLongPosition().getPositionInfo().setTraderName(getName());
        } else {
          getLongPosition().addOrder(order);
        }
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Error occurred while posting limit BUY order");
      notifyUser(prepareOrderFailedNotificationMessage(e.getMessage(), true));
    }

    return order;
  }

  //todo использовался для safety (разве openSell не покрывает случай с limit-ордерами?)
  //@Override
  public OrderInfoObject limitSell(DealInfo info) {
    float desiredRate = info.getDealRate();
    String symbol = getSymbol();
    getLogger().info(String.format("Posting limit SELL order on %s: %s", symbol, desiredRate));
    double dealAmount = getDealAmount(desiredRate, false);
    OrderInfoObject order = null;
    try {
      order = getOrdersProcessor().limitOrder(getSignedClient(), desiredRate,
          symbol, dealAmount, false, leverage);
      if (order != null) {
        if (getShortPosition() == null) {
          shortPosition = getOrdersProcessor().createPositionContainer(order);
          getShortPosition().getPositionInfo().setComment((String) info.getParameter(DealInfo.PARAM_STRATEGY));
          getShortPosition().getPositionInfo().setTraderId(getId());
          getShortPosition().getPositionInfo().setTraderName(getName());
        } else {
          getShortPosition().addOrder(order);
        }
      }
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Error occurred while posting limit SELL order");
      notifyUser(prepareOrderFailedNotificationMessage(e.getMessage(), false));
    }

    return order;
  }

  @Override
  public boolean closeBuy(DealInfo info) {
    if (hasOpenedLongPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      getLogger().info(String.format("%s. Closing BUY deal on %s by request (%s) at %s",
          getName(), getSymbol(), strategy, closingRate));
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      getLongPosition().update(getSignedClient());
      BigDecimal positionAmount = getLongPosition().getAvgPrice().multiply(getLongPosition().getExecutedAmount());
      getLongPosition().cancelSafetyOrders(getSignedClient());
      OrderInfoObject closeOrder = getLongPosition().closeDeal(getSignedClient());
      if (closeOrder != null) {
        float profit = (float) (closeOrder.getPrice() * closeOrder.getAmount() - positionAmount.doubleValue());
        long closeTime = closeOrder.getOrderTimestamp().getTime();
        LoggingUtils.logDealCloseInfo(this.toString(),
            getLongPosition().getPositionInfo().getOpenTimestamp().getTime(), closeTime,
            baseCurrency, quotedCurrency, getLongPosition().getPositionInfo().getAveragePrice(), closeOrder.getPrice(),
            getLongPosition().getExecutedAmount(), getLongPosition().getSide(), profit,
            getLongPosition().getPositionInfo().getComment() + ":" + strategy,
            getLogger());
        accountProfit(profit, getLongPosition());
        finishPosition(getLongPosition(), closeTime, strategy);
        notifyUser(prepareDealClosedNotificationMessage("BUY", closeOrder.getPrice(), profit, strategy));
        longPosition = null;
        return true;
      } else {
        //todo завершить позицию, уведомить пользователя о неуспешном закрытии
      }
    }
    return false;
  }

  @Override
  public boolean closeSell(DealInfo info) {
    if (hasOpenedShortPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      getLogger().info(String.format("%s. Closing SELL deal on %s by request (%s) at %s",
          getName(), getSymbol(), strategy, closingRate));
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      getShortPosition().update(getSignedClient());
      BigDecimal positionAmount = getShortPosition().getAvgPrice().multiply(getShortPosition().getExecutedAmount());
      getShortPosition().cancelSafetyOrders(getSignedClient());
      OrderInfoObject closeOrder = getShortPosition().closeDeal(getSignedClient());
      if (closeOrder != null) {
        float profit = (float) (positionAmount.doubleValue() - closeOrder.getPrice() * closeOrder.getAmount());
        long closeTime = closeOrder.getOrderTimestamp().getTime();
        LoggingUtils.logDealCloseInfo(this.toString(),
            getShortPosition().getPositionInfo().getOpenTimestamp().getTime(), closeTime,
            baseCurrency, quotedCurrency, getShortPosition().getPositionInfo().getAveragePrice(), closeOrder.getPrice(),
            getShortPosition().getExecutedAmount(), getShortPosition().getSide(), profit,
            getShortPosition().getPositionInfo().getComment() + ":" + strategy,
            getLogger());
        accountProfit(profit, getShortPosition());
        finishPosition(getShortPosition(), closeTime, strategy);
        notifyUser(prepareDealClosedNotificationMessage("SELL", closeOrder.getPrice(), profit, strategy));
        shortPosition = null;
        return true;
      } else {
        //todo завершить позицию, уведомить пользователя о неуспешном закрытии
      }
    }
    return false;
  }

  @Override
  public boolean changeStopBuy(DealInfo info) {
    if (hasOpenedLongPositions())
      return changeOrderStopLevel(info, getLongPosition());
    LOGGER.info("Request received but no long position is presented");
    return false;
  }

  @Override
  public boolean changeStopSell(DealInfo info) {
    if (hasOpenedShortPositions())
      return changeOrderStopLevel(info, getShortPosition());
    LOGGER.info("Request received but no short position is presented");
    return false;
  }

  private boolean changeOrderStopLevel(DealInfo info, AbstractPositionContainer position) {
    getLogger().debug(this + ". changeOrderStopLevel, DealInfo: " + info);
    if (position == null) {
      getLogger().warn("Trying to adjust stop-orders for non-existing position on " + getSymbol());
      return false;
    }
    //todo можно по настройке на уровне SL выставлять трейлинг стоп на новую сделку в том же направлении
    boolean forceSLChange = info.hasParameter(DealInfo.PARAM_FORCE_STOP_CHANGE) &&
        (boolean) info.getParameter(DealInfo.PARAM_FORCE_STOP_CHANGE);
    boolean result = false;
    StringBuilder sb = new StringBuilder();
    if (info.getStopLoss() != null && (forceSLChange || !hasBetterStopLoss(position, info.getStopLoss(), 0.00025f))) {
      if (position.rearrangeStopLoss(getSignedClient(), BigDecimal.valueOf(info.getStopLoss())) != null) {
        sb.append(prepareStopChangedNotificationMessage(position.getSide(), "Stop-loss",
            info.getStopLoss(), position.getExpectedProfit(position.getStopLossOrder().getPrice()),
            String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
        result = true;
      } else {//todo по-хорошему надо бы пробрасывать как-то причину
        sb.append(prepareStopNotChangedNotificationMessage(position.getSide(), "Stop-loss", info.getStopLoss(),
            String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
      }
    }
    if (info.getTakeProfit() != null && checkTakeProfit(position, info.getTakeProfit(), 0.0005f)) {
      if (position.rearrangeTakeProfit(getSignedClient(), BigDecimal.valueOf(info.getTakeProfit())) != null) {
        if (!sb.isEmpty())
          sb.append(System.lineSeparator());
        sb.append(prepareStopChangedNotificationMessage(position.getSide(), "Take-profit",
            info.getTakeProfit(), position.getExpectedProfit(position.getTakeProfitOrder().getPrice()),
            String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
        result = true;
      } else {//todo по-хорошему надо бы пробрасывать как-то причину
        sb.append(prepareStopNotChangedNotificationMessage(position.getSide(), "Take-profit", info.getStopLoss(),
            String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
      }
    }
    if (!sb.isEmpty())
      notifyUser(sb.toString());
    return result;
  }

  @Override
  protected double getDealAmount(float rate, boolean buy) {
    if (amountDeterminant == AmountDeterminant.PERCENT_BASE)
      throw new UserException("Percent base currency amount determinant is not applicable for this trader");
    /*todo не понял, это зачем if (amountDeterminant == AmountDeterminant.FIXED_BASE) {
      return amountValue * rate;
    }*/
    double result = super.getDealAmount(rate, buy);
    if (getSafetyOrdersAmountAddedPercent() > 0.f) {
      if (buy && hasOpenedLongPositions()) {//todo брать не последний, а максимальный
        result = getLongPosition().getMaxOrder().getAmount() * (1.f + getSafetyOrdersAmountAddedPercent());
      } else if (!buy && hasOpenedShortPositions()) {
        result = getShortPosition().getMaxOrder().getAmount() * (1.f + getSafetyOrdersAmountAddedPercent());
      }
    }
    float maxDealAmount = getDealMarginLimit();
    if (result * rate > maxDealAmount) {
      getLogger().info(this + ". User's plan doesn't allow so big deals - " + (result * rate) + " " + quotedCurrency);
      return maxDealAmount / rate;
    }
    return result;
  }

  private float getDealMarginLimit() {
    AppUser user = findOwnerUser();
    if (user != null && "admin".equals(user.getUserName()))
    /*if (getAuthenticator() != null &&
        "Binance API".equalsIgnoreCase(getAuthenticator().getName()))*/
      return 500f;
    return 250f;//todo или снизить до 100, но делить на leverage?
  }

  /**
   * Возвращает список последних исполненных ордеров
   * @param count - количество запрашиваемых ордеров
   */
  protected abstract String loadLastOpenOrders(int count);

  /**
   * Возвращает список текущих открытых, но не выполненных ордеров
   * @param count - количество запрашиваемых ордеров
   */
  protected abstract String loadActiveOrders(int count);

  protected abstract boolean canTrade();

  protected void accountProfit(float profit, AbstractPositionContainer position) {
    quotedCurrencyAmount += profit;
    quotedCurrencyTotalAmount += profit;
    position.getPositionInfo().setProfit(profit);
    //BigDecimal freeFunds = position.getExecutedAmount().multiply(position.getAvgPrice());
    //quotedCurrencyAmount += freeFunds.floatValue() / leverage;
  }

  protected void finishPosition(AbstractPositionContainer position, Long closeTime, String initiator) {
    PositionInfoObject posInfo = position.getPositionInfo();
    posInfo.setCloseTimestamp(new Timestamp(closeTime));
    posInfo.setStatus(PositionInfoObject.PositionStatus.CLOSED.name());
    posInfo.setComment(posInfo.getComment() + ":" + initiator);
  }

  protected void cancelPosition(AbstractPositionContainer position, Long closeTime, String initiator) {
    PositionInfoObject posInfo = position.getPositionInfo();
    posInfo.setCloseTimestamp(new Timestamp(closeTime));
    posInfo.setStatus(PositionInfoObject.PositionStatus.CANCELLED.name());
    posInfo.setComment(posInfo.getComment() + ":" + initiator);
  }

  private void updateOneOrderRecord(OrderInfoObject o, String orderType, PositionInfo posInfo) {
    /*OrderInfoObject orderObj = DatabaseInteractor.loadOrCreateOrder(o);
    if (orderObj.isNew()) {
      orderObj.setType(orderType);
      orderObj.setPositionId(posInfo.getPositionId());//либо брать из ордера-источника
    }
    orderObj.update(o);
    if (!Objects.equals(o.getPositionId(), orderObj.getPositionId())) {
      LOGGER.warn(String.format("Position id mismatch for order %s: %s != %s",
          orderObj.getOrderId(), orderObj.getPositionId(), posInfo.getPositionId()));
      orderObj.setPositionId(posInfo.getPositionId());
    }
    DatabaseInteractor.saveOrderObject(orderObj);*/
  }

  @Override
  public String setParameter(String name, String value) {
    switch (name) {
      case "telegramNotificationsChat" -> {
        return setTgChatId(value);
      }
      case "leverage" -> {
        return setLeverage(Integer.parseInt(value));
      }
      case "useDatabase" -> {
        return setStoreOrdersInDatabase(Boolean.parseBoolean(value));
      }
      case "newOrderLivePeriodMs" -> {
        return setOrderLivePeriod(Long.parseLong(value));
      }
      case "safetyOrdersMinimalDistancePercent" -> {
        return setSafetyOrdersMinimalDistancePercent(Float.parseFloat(value));
      }
      case "safetyOrdersMinimalDistanceFactor" -> {
        return setSafetyOrdersMinimalDistanceFactor(Float.parseFloat(value));
      }
      case "safetyOrdersAmountAddedPercent" -> {
        return setSafetyOrdersAmountAddedPercent(Float.parseFloat(value));
      }
      case "maxSafetyOrdersLimit" -> {
        return setMaxSafetyOrdersLimit(Integer.parseInt(value));
      }
      case "allowPositionIncreasing" -> {
        return setAllowPositionIncreasing(Boolean.parseBoolean(value));
      }
      case "allowUnprofitableTakeProfit" -> {
        return setAllowUnprofitableTakeProfit(Boolean.parseBoolean(value));
      }
      case "allowFartherStopLoss" -> {
        return setAllowFartherStopLoss(Boolean.parseBoolean(value));
      }
      case "openLongPositionMarket" -> {//todo после реализовать через REST
        //todo перед открытием проверять статус текущих позиций (вдруг были и уже закрылись)
        if (marketBuy(value == null ? null : Double.parseDouble(value)))
          return "Successfully opened";
        else
          return "Unsuccessful opening";
      }
      case "openShortPositionMarket" -> {//todo после реализовать через REST
        //todo перед открытием проверять статус текущих позиций (вдруг были и уже закрылись)
        if (marketSell(value == null ? null : Double.parseDouble(value)))
          return "Successfully opened";
        else
          return "Unsuccessful opening";
      }
      case "openLongPositionLimit" -> {//todo после реализовать через REST
        //todo перед открытием проверять статус текущих позиций (вдруг были и уже закрылись)
        if (openBuy(prepareLimitOrderDealInfo(Float.parseFloat(value))) != null)
          return "Successfully opened";
        else
          return "Unsuccessful opening";
      }
      case "openShortPositionLimit" -> {//todo после реализовать через REST
        //todo перед открытием проверять статус текущих позиций (вдруг были и уже закрылись)
        if (openSell(prepareLimitOrderDealInfo(Float.parseFloat(value))) != null)
          return "Successfully opened";
        else
          return "Unsuccessful opening";
      }
      case "closeLongPosition" -> {//todo после реализовать через REST
        if (closeBuy(getManualCloseDealInfo()))
          return "Successfully closed";
        else
          return "Unsuccessful closing";
      }
      case "closeShortPosition" -> {//todo после реализовать через REST
        if (closeSell(getManualCloseDealInfo()))
          return "Successfully closed";
        else
          return "Unsuccessful closing";
      }
      case "changeLongStopLoss" -> {
        if (changeStopBuy(getChangeStopDealInfo(value, null)))//todo после реализовать через REST
          return "Successfully changed";
        else
          return "Unsuccessful change";
      }
      case "changeLongTakeProfit" -> {
        if (changeStopBuy(getChangeStopDealInfo(null, value)))//todo после реализовать через REST
          return "Successfully changed";
        else
          return "Unsuccessful change";
      }
      case "changeShortStopLoss" -> {
        if (changeStopSell(getChangeStopDealInfo(value, null)))//todo после реализовать через REST
          return "Successfully changed";
        else
          return "Unsuccessful change";
      }
      case "changeShortTakeProfit" -> {
        if (changeStopSell(getChangeStopDealInfo(null, value)))//todo после реализовать через REST
          return "Successfully changed";
        else
          return "Unsuccessful change";
      }
      case "showLastOpenOrders" -> {
        return loadLastOpenOrders(Integer.parseInt(value));
      }
      case "showCurrentActiveOrders" -> {
        return loadActiveOrders(Integer.parseInt(value));
      }
      case "attachToExistingPositions" -> {
        return setAttachToExistingPositions(Boolean.parseBoolean(value));
      }
      case "addOpenedLongOrder" -> {
        return addOpenedLongOrders(value);
      }
      case "addOpenedShortOrder" -> {
        return addOpenedShortOrders(value);
      }
      case "addLastOpenedLongOrder" -> {
        return addLastOpenedLongOrder();
      }
      case "addLastOpenedShortOrder" -> {
        return addLastOpenedShortOrder();
      }
      default -> {
        return super.setParameter(name, value);
      }
    }
  }

  protected String addOpenedLongOrders(String orderIds) {
    if (baseCurrency == null && quotedCurrency == null) {
      LOGGER.warn("Can not add long order " + orderIds + " without a symbol");
      return null;
    }
    StringBuilder result = new StringBuilder();
    for (String orderId : orderIds.split(",")) {
      boolean orderResult = addOpenedLongOrder(orderId);
      result.append("Order with id ").append(orderId).
          append(orderResult ? " added successfully" : "can not be added");
    }
    return result.toString();
  }

  protected String addOpenedShortOrders(String orderIds) {
    if (baseCurrency == null && quotedCurrency == null) {
      LOGGER.warn("Can not add long order " + orderIds + " without a symbol");
      return null;
    }
    StringBuilder result = new StringBuilder();
    for (String orderId : orderIds.split(",")) {
      boolean orderResult = addOpenedShortOrder(orderId);
      result.append("Order with id ").append(orderId).
          append(orderResult ? " added successfully" : "can not be added");
    }
    return result.toString();
  }

  protected String addLastOpenedLongOrder() {
    return addOpenedLongOrder(null) ? "Order added successfully" : "Order cannot be added";
  }

  protected String addLastOpenedShortOrder() {
    return addOpenedShortOrder(null) ? "Order added successfully" : "Order cannot be added";
  }

  protected boolean addOpenedLongOrder(String orderId) {
    try {
      String symbol = getSymbol();
      if (orderId == null) {
        orderId = findLastOpenedOrder(true, getOrdersProcessor());
      }
      if (orderId == null) {
        getLogger().warn("No orderId provided");
        return false;
      }
      OrderInfoObject order = getOrdersProcessor().queryOrder(getSignedClient(), symbol, orderId);
      //todo автоматически искать существующий SL/TP
      if (getLongPosition() == null) {
        longPosition = getOrdersProcessor().createPositionContainer(order);
        getLongPosition().getPositionInfo().setComment("Manual");
        //longPosition.setParameter(AbstractPositionContainer.PARAM_COMMENT, "Manual");
        dealOpened();
      } else {
        for (OrderInfoObject o : getLongPosition().getOrders()) {
          if (o.equals(order)) {
            getLogger().warn("Order container already contains long order " + orderId + " on " + symbol);
            return false;
          }
        }
        getLongPosition().addOrder(order);
        getLongPosition().incrementSafetyOrdersTriggered();
      }
      checkOpenedPosition(getLongPosition(), null);
      return true;
    } catch (Exception e) {
      getLogger().warn("Error occurred while querying order with id " + orderId, e);
      return false;
    }
  }

  protected boolean addOpenedShortOrder(String orderId) {
    try {
      String symbol = getSymbol();
      if (orderId == null) {
        orderId = findLastOpenedOrder(false, getOrdersProcessor());
      }
      if (orderId == null) {
        getLogger().warn("No orderId provided");
        return false;
      }
      OrderInfoObject order = getOrdersProcessor().queryOrder(getSignedClient(), symbol, orderId);
      //todo автоматически искать существующий SL/TP
      if (getShortPosition() == null) {
        shortPosition = getOrdersProcessor().createPositionContainer(order);
        getShortPosition().getPositionInfo().setComment("Manual");
        //shortPosition.setParameter(AbstractPositionContainer.PARAM_COMMENT, "Manual");
        dealOpened();
      } else {
        for (OrderInfoObject o : getShortPosition().getOrders()) {
          if (o.equals(order)) {
            getLogger().warn("Order container already contains short order " + orderId + " on " + symbol);
            return false;
          }
        }
        getShortPosition().addOrder(order);
        getShortPosition().incrementSafetyOrdersTriggered();
      }
      checkOpenedPosition(getShortPosition(), null);
      return true;
    } catch (Exception e) {
      getLogger().warn("Error occurred while querying order with id " + orderId, e);
      return false;
    }
  }

  protected abstract String findLastOpenedOrder(boolean buy, AbstractOrdersProcessor processor);

  @Override
  public String canBuy(float desiredRate) {
    if (!supportsHedgeMode() && hasOpenedShortPositions())
      return "Cant' open long position while short one exists";
    if (!canTrade())
      return "Futures trading is not available for the account";
    if (getLongPosition() != null) {
      if (getLongPosition().getOrders().size() >= getMaxSafetyOrdersLimit() + 1)
        return "Can't open any more buy orders";
      if (!allowPositionIncreasing && orderIsBetterThan(true, desiredRate, getBestBuyOrder()))
        return "Better BUY order exists";
      if (safetyOrdersMinimalDistancePercent > 0.f || safetyOrdersMinimalDistanceFactor != null) {
        float minimalDistance = getSafetyOrdersMinimalDistance();
        getLogger().trace("Current minimal SOD = " + String.format(Locale.ROOT, "%.5f", minimalDistance));
        for (OrderInfoObject o : getLongPosition().getOrders()) {
          if (closeOrderExists(desiredRate, o, minimalDistance))
            return "New BUY order is too close to opened ones";
        }
      }
    }
    return super.canBuy(desiredRate);
  }

  @Override
  public String canSell(float desiredRate) {
    if (!supportsHedgeMode() && hasOpenedLongPositions())
      return "Cant' open short position while long one exists";
    if (!canTrade())
      return "Futures trading is not available for the account";
    if (getShortPosition() != null) {
      if (getShortPosition().getOrders().size() >= getMaxSafetyOrdersLimit() + 1)
        return "Can't open any more sell orders";
      if (!allowPositionIncreasing && orderIsBetterThan(false, desiredRate, getBestSellOrder()))
        return "Better SELL order exists";
      if (safetyOrdersMinimalDistancePercent > 0.f || safetyOrdersMinimalDistanceFactor != null) {
        for (OrderInfoObject o : getShortPosition().getOrders()) {
          float minimalDistance = getSafetyOrdersMinimalDistance();
          getLogger().trace("Current minimal SOD = " + String.format(Locale.ROOT, "%.5f", minimalDistance));
          if (closeOrderExists(desiredRate, o, minimalDistance))
            return "New SELL order is too close to opened ones";
        }
      }
    }
    return super.canSell(desiredRate);
  }

  public String setLeverage(int leverage) {
    if (!Objects.equals(this.leverage, leverage)) {
      this.leverage = leverage;
      return String.format("Reconfigure trader %s. New leverage = %s", getName(), leverage);
    }
    return null;
  }

  @Override
  public Integer getLeverage() {
    /*if (leverage == null)
      leverage = getOrdersProcessor().getLeverage(getSignedClient(), getSymbol());*/
    if (leverage == null) {
      getLogger().warn(this + ". Leverage is not set. Using default value - " + DEFAULT_LEVERAGE_VALUE);
      return DEFAULT_LEVERAGE_VALUE;
    }
    return leverage;
  }

  @NotNull
  private static DealInfo getChangeStopDealInfo(String sl, String tp) {
    DealInfo dealInfo = new DealInfo(0.f,
        sl == null ? null : Float.parseFloat(sl),
        tp == null ? null : Float.parseFloat(tp));
    dealInfo.addParameter(DealInfo.PARAM_FORCE_STOP_CHANGE, Boolean.TRUE);
    dealInfo.addParameter(DealInfo.PARAM_STRATEGY, "manual");
    return dealInfo;
  }

  private DealInfo getManualCloseDealInfo() {
    DealInfo dealInfo = new DealInfo(0.f, (Float) null, null);
    dealInfo.addParameter(DealInfo.PARAM_STRATEGY, "manual");
    return dealInfo;
  }

  /*private int getMaxOrdersCountAllowed() {
    return 20;
  }*/

  //todo важный коэффициент, сделать настраиваемым
  private static float getSafetyOrdersTPCorrectionFactor() {
    return 0.5f;
  }

  public boolean storeOrdersInDatabase() {
    return storeOrdersInDatabase;
  }

  public String setStoreOrdersInDatabase(boolean value) {
    if (!Objects.equals(storeOrdersInDatabase, value)) {
      this.storeOrdersInDatabase = value;
      return String.format("New useDatabase = %s", value);
    }
    return null;
  }

  private String setOrderLivePeriod(long value) {
    if (!Objects.equals(newOrderLivePeriod, value)) {
      this.newOrderLivePeriod = value;
      return String.format("New newOrderLivePeriod = %sms", value);
    }
    return null;
  }

  protected long getOrderLivePeriod(AbstractPositionContainer posInfo) {
    if (posInfo.hasParameter(DealInfo.PARAM_MANUAL_LIMIT))
      return TimeUtils.HOUR_IN_MS * 24L;
    return newOrderLivePeriod;
  }

  private String setSafetyOrdersMinimalDistancePercent(float value) {
    if (!Objects.equals(safetyOrdersMinimalDistancePercent, value / 100.f)) {
      this.safetyOrdersMinimalDistancePercent = value / 100.f;
      return String.format("New safetyOrdersMinDistPercent = %s%%", value);
    }
    return null;
  }

  private String setSafetyOrdersMinimalDistanceFactor(float value) {
    if (!Objects.equals(safetyOrdersMinimalDistanceFactor, value) && value > 0.f) {
      this.safetyOrdersMinimalDistanceFactor = value;
      return String.format("New safetyOrdersMinDistFactor = %s", value);
    }
    return null;
  }

  public float getSafetyOrdersAmountAddedPercent() {
    return safetyOrdersAmountAddedPercent;
  }

  public String setSafetyOrdersAmountAddedPercent(float value) {
    if (!Objects.equals(safetyOrdersAmountAddedPercent, value / 100.f)) {
      this.safetyOrdersAmountAddedPercent = value / 100.f;
      return String.format("New safetyOrdersAmountAddedPercent = %s%%", value);
    }
    return null;
  }

  public int getMaxSafetyOrdersLimit() {
    return maxSafetyOrdersLimit;
  }

  public String setMaxSafetyOrdersLimit(int value) {
    if (!Objects.equals(maxSafetyOrdersLimit, value)) {
      this.maxSafetyOrdersLimit = value;
      return String.format("New maxSafetyOrdersLimit = %s%%", value);
    }
    return null;
  }

  private String setAllowPositionIncreasing(boolean value) {
    if (!Objects.equals(allowPositionIncreasing, value)) {
      this.allowPositionIncreasing = value;
      return String.format("New allowPositionIncreasing = %s", value);
    }
    return null;
  }

  private String setAllowUnprofitableTakeProfit(boolean value) {
    if (!Objects.equals(allowUnprofitableTakeProfit, value)) {
      this.allowUnprofitableTakeProfit = value;
      return String.format("New allowUnprofitableTakeProfit = %s", value);
    }
    return null;
  }

  private String setAllowFartherStopLoss(boolean value) {
    if (!Objects.equals(allowFartherStopLoss, value)) {
      this.allowFartherStopLoss = value;
      return String.format("New allowFartherStopLoss = %s", value);
    }
    return null;
  }

  public boolean attachToExistingPositions() {
    return attachToExistingPositions;
  }

  public String setAttachToExistingPositions(boolean value) {
    if (!Objects.equals(attachToExistingPositions, value)) {
      this.attachToExistingPositions = value;
      return String.format("New attachToExistingPositions = %s", value);
    }
    return null;
  }

  protected boolean orderIsBetterThan(boolean buy, float rate, OrderInfoObject order) {
    if (order == null)
      return true;
    return (buy && order.getPrice() < rate) || (!buy && order.getPrice() > rate);
  }

  private boolean closeOrderExists(float rate, OrderInfoObject order, float minimalDistance) {
    float oPrice = order.getPrice();
    return Math.abs(oPrice - rate) / oPrice < minimalDistance;
  }

  private float getSafetyOrdersMinimalDistance() {
    if (safetyOrdersMinimalDistanceFactor != null && lastAppc > 0)
      return safetyOrdersMinimalDistanceFactor * lastAppc;
    return safetyOrdersMinimalDistancePercent;
  }

  protected OrderInfoObject getBestSellOrder() {
    float bestPrice = Float.MIN_VALUE;
    OrderInfoObject bestOrder = null;
     if (shortPosition != null) {
      for (OrderInfoObject order : shortPosition.getOrders()) {
        float oPrice = order.getPrice();
        if (oPrice > bestPrice) {
          bestOrder = order;
          bestPrice = oPrice;
        }
      }
    }
    return bestOrder;
  }

  protected OrderInfoObject getBestBuyOrder() {
    float bestPrice = Float.MAX_VALUE;
    OrderInfoObject bestOrder = null;
    if (longPosition != null) {
      for (OrderInfoObject order : longPosition.getOrders()) {
        float oPrice = order.getPrice();
        if (oPrice < bestPrice) {
          bestOrder = order;
          bestPrice = oPrice;
        }
      }
    }
    return bestOrder;
  }

  public AbstractPositionContainer getLongPosition() {
    return longPosition;
  }

  public AbstractPositionContainer getShortPosition() {
    return shortPosition;
  }

  @Override
  public boolean hasOpenedLongPositions() {
    return getLongPosition() != null;
  }

  @Override
  public boolean hasOpenedShortPositions() {
    return getShortPosition() != null;
  }

  @Override
  public float getCurrentLongProfitability(float curRate) {
    if (getLongPosition() != null) {
      return curRate / getLongPosition().getPositionInfo().getAveragePrice() - 1.f;
    }
    return super.getCurrentLongProfitability(curRate);
  }

  @Override
  public float getCurrentShortProfitability(float curRate) {
    if (getShortPosition() != null) {
      return getShortPosition().getPositionInfo().getAveragePrice() / curRate - 1.f;
    }
    return super.getCurrentLongProfitability(curRate);
  }

  @Override
  public DealInfo getOpenLongPositionInfo() {
    return prepareOpenPositionInfo(getLongPosition());
  }

  @Override
  public String getOpenLongPositionDetails() {
    return getLongPosition().toString(null);
  }

  @Override
  public DealInfo getOpenShortPositionInfo() {
    return prepareOpenPositionInfo(getShortPosition());
  }

  @Override
  public String getOpenShortPositionDetails() {
    return getShortPosition().toString(null);
  }

  private DealInfo prepareOpenPositionInfo(AbstractPositionContainer position) {
    if (position == null)
      return null;
    DealInfo result = new DealInfo(position.getPositionInfo().getAveragePrice(),
        position.getStopLossOrder() == null ? null :position.getStopLossOrder().getPrice(),
        position.getTakeProfitOrder() == null ? null : position.getTakeProfitOrder().getPrice());
    if (position.getPositionInfo().getOpenTimestamp() != null)
      result.addParameter(DealInfo.PARAM_POSITION_TIME, position.getPositionInfo().getOpenTimestamp().getTime());
    return result;
  }

  private DealInfo prepareLimitOrderDealInfo(float price) {
    DealInfo dealInfo = new DealInfo(price, (Float) null, null);
    dealInfo.addParameter(DealInfo.PARAM_MANUAL_LIMIT, Boolean.TRUE);
    return dealInfo;
  }

  protected String getSymbol() {//todo кешировать, всё равно не меняется
    return TradeUtils.getPair(baseCurrency, quotedCurrency);
  }

  private String prepareOpenDealMessage(String direction, float desiredRate, Float stopLoss, Float takeProfit) {
    StringBuilder sb = new StringBuilder("Opening ");
    sb.append(direction).append(" deal on ").append(getSymbol());
    sb.append(" at ").append(Utils.formatFloatValue(desiredRate, 8));
    if (stopLoss != null)
      sb.append(" SL: ").append(Utils.formatFloatValue(stopLoss, 6)).
          append(" (").append(Utils.formatFloatValue(stopLoss / desiredRate * 100.f, 3)).append("%)");
    if (takeProfit != null)
      sb.append(" TP: ").append(Utils.formatFloatValue(takeProfit, 6)).
          append(" (").append(Utils.formatFloatValue(takeProfit / desiredRate * 100.f, 3)).append("%)");
    return sb.toString();
  }

  private Float getCorrectedTakeProfit(Float newTp, float dealRate, AbstractPositionContainer position) {
    if (position == null || newTp == null)
      return newTp;
    float avgPrice = position.getPositionInfo().getAveragePrice(),
        tpPrice = position.getTakeProfitOrder().getPrice();
    //float actualTpPercent = tpPrice / avgPrice - 1.f;
    float actualTpPercent = (tpPrice - avgPrice) / avgPrice;
    //float newTpPercent = newTp / dealRate - 1.f;
    float newTpPercent = (newTp - dealRate) / dealRate;
    float resultTpPercent = ((actualTpPercent + newTpPercent) / 2f) * getSafetyOrdersTPCorrectionFactor();
    float newAvgPrice = getFutureAvgPrice(position, dealRate);
    float resultTp = (resultTpPercent + 1f) * newAvgPrice;
    getLogger().info(String.format("New position TP corrected. Actual value: %.6f. Input value: %.6f. New value: %.6f",
        tpPrice, newTp, resultTp));
    return resultTp;
  }

  private float getFutureAvgPrice(AbstractPositionContainer position, float dealRate) {
    //todo грубое вычисление скорректированной средней цены, сделать нормально с учётом размеров позиций
    int ordersCount = position.getTriggeredOrdersCount();
    float result = (position.getPositionInfo().getAveragePrice() * ordersCount + dealRate) / (ordersCount + 1);
    getLogger().debug("Future estimated avg position rate evaluated - " + result);
    return result;
  }

  protected abstract AbstractOrdersProcessor getOrdersProcessor();

  @Override
  public boolean checkOpenedPositions(AbstractRate<?> lastRate) {
    //todo трейлинг переходит на FILLED, когда открывается по факту,
    // и меняет тип на MARKET, origType остаётся TRAILING_STOP_MARKET, updateTime обновляется
    try {
      if (hasOpenedLongPositions()) {
        if (checkOpenedPosition(getLongPosition(), lastRate)) {
          longPosition = null;
        }
      }
    } catch (SocketException e) {
      LoggingUtils.logError(getLogger(), e, "Temporary error while processing BUY position of " + getName());
    } catch (Exception e) {
      PositionInfoObject position = longPosition.getPositionInfo();
      position.setStatus(PositionInfoObject.PositionStatus.ERROR.name());
      notifyUser(preparePositionErrorMessage(position.getDirection(), position.getPositionId()));
      longPosition = null;// чтобы не возникала ошибка при каждом запуске
      LoggingUtils.logError(getLogger(), e, "Processing BUY orders error " + getName());
    }
    try {
      if (hasOpenedShortPositions()) {
        if (checkOpenedPosition(getShortPosition(), lastRate)) {
          shortPosition = null;
        }
      }
    } catch (SocketException e) {
      LoggingUtils.logError(getLogger(), e, "Temporary error while processing SELL position of " + getName());
    } catch (Exception e) {
      PositionInfoObject position = shortPosition.getPositionInfo();
      position.setStatus(PositionInfoObject.PositionStatus.ERROR.name());
      notifyUser(preparePositionErrorMessage(position.getDirection(), position.getPositionId()));
      shortPosition = null;// чтобы не возникала ошибка при каждом запуске
      LoggingUtils.logError(getLogger(), e, "Processing SELL orders error " + getName());
    }
    return hasOpenedShortPositions() || hasOpenedLongPositions();
  }

  /**
   * Проверяет пакет ордеров
   *
   * @param posInfo - пакет ордеров, состоящий из базового, тейк-профита, стоп-лосса и набора страховочных
   * @param lastRate - последняя свеча
   * @return true, если пакет завершён и его можно удалять, false - иначе
   */
  //TODO научиться пользоваться UserDataStream
  //TODO сделать что-то с этой неочевидной логикой возврата boolean и реакцией на возвр. значение
  //TODO оптимизировать сохранение состояния в БД, сейчас повсюду вызовы DatabaseInteractor.updateFinishedDeal
  protected boolean checkOpenedPosition(@NotNull AbstractPositionContainer posInfo, @Nullable AbstractRate<?> lastRate)
    throws Exception {
    boolean success = posInfo.update(getSignedClient()) > 0;
    if (success) {
      long checkTime = lastRate != null ? lastRate.getCloseTime() : DateUtils.currentTimeMillis();
      boolean positionIsOpen = posInfo.someOrdersFilled();
      PositionInfo position = posInfo.getPositionInfo();
      if (!positionIsOpen) {
        if (checkTime - position.getOpenTimestamp().getTime() > getOrderLivePeriod(posInfo)) {
          int cancelled = posInfo.cancel(getSignedClient());
          if (cancelled > 0) {//todo убедиться, что основной отменился, а не только стопы
            //todo добавить метод isCancelled в контейнере позиции, который будет проверять статус вложенных ордеров
            // если он вернёт false (значит какой-то из ордеров уже исполнен), закрывать позицию вместо отмены ордера
            cancelPosition(posInfo, checkTime, "unfired");
            getLogger().info(String.format("%s order on %s is not filled and cancelled",
                position.getDirection(), position.getSymbol()));
            if (cancelled < 3)
              getLogger().info(
                  "Some of the additional orders could be left uncanceled, cancelled " + cancelled + " order(s)");
            notifyUser(prepareExpiredOrderCancelledMessage(position.getDirection(), position.getPositionId()));
            return true;
          }
        }
      } else {
        OrderInfoObject stopLossOrder = posInfo.getStopLossOrder();
        OrderInfoObject takeProfitOrder = posInfo.getTakeProfitOrder();
        BigDecimal avgPrice = posInfo.getAvgPrice();
        if (stopLossOrder != null && OrderInfoObject.OrderStatus.FILLED.equals(stopLossOrder.getStatus())) {
          float stopPrice = stopLossOrder.getAvgPrice().floatValue();
          getLogger().debug(String.format("%s position on %s closed by stop-loss at %s",
              position.getDirection(), position.getSymbol(), stopPrice));
          finishPosition(posInfo, stopLossOrder.getOrderTimestamp().getTime(), STOP_LOSS_PARAM);
          BigDecimal executedAmount = posInfo.getExecutedAmount();//вычисляем до отмены страховочных
          float profit = evaluateProfit(avgPrice.floatValue(), stopPrice, executedAmount, position.getDirection());
          LoggingUtils.logDealCloseInfo(this.toString(),
              position.getOpenTimestamp().getTime(), position.getCloseTimestamp().getTime(),
              baseCurrency, quotedCurrency, avgPrice.floatValue(), stopPrice,
              executedAmount, position.getDirection(), profit,
              posInfo.getPositionInfo().getComment(), BinanceFuturesTrader.LOGGER);
          accountProfit(profit, posInfo);
          notifyUser(prepareDealClosedNotificationMessage(position.getDirection(), stopPrice, profit, STOP_LOSS_PARAM));
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn(String.format("Unsuccessful cancelling of safety orders for position %s",
                position.getPositionId()));
          return true;
        } else if (takeProfitOrder != null && OrderInfoObject.OrderStatus.FILLED.equals(takeProfitOrder.getStatus())) {
          float stopPrice = takeProfitOrder.getAvgPrice().floatValue();
          getLogger().debug(String.format("%s position on %s closed by take-profit at %s",
              position.getDirection(), position.getSymbol(), stopPrice));
          finishPosition(posInfo, takeProfitOrder.getOrderTimestamp().getTime(), TAKE_PROFIT_PARAM);
          BigDecimal executedAmount = posInfo.getExecutedAmount();//вычисляем до отмены страховочных
          float profit = evaluateProfit(avgPrice.floatValue(), stopPrice, executedAmount, position.getDirection());
          LoggingUtils.logDealCloseInfo(this.toString(),
              position.getOpenTimestamp().getTime(), position.getCloseTimestamp().getTime(),
              baseCurrency, quotedCurrency, avgPrice.floatValue(), stopPrice,
              executedAmount, position.getDirection(), profit,
              posInfo.getPositionInfo().getComment(), BinanceFuturesTrader.LOGGER);
          accountProfit(profit, posInfo);
          notifyUser(prepareDealClosedNotificationMessage(position.getDirection(), stopPrice, profit, TAKE_PROFIT_PARAM));
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn(String.format("Unsuccessful cancelling of safety orders for position %s",
                position.getPositionId()));
          return true;
        } else if (posInfo.getStopLossOrder() == null && posInfo.getTakeProfitOrder() == null) {
          getLogger().warn(String.format("Order container for base %s has no SL nor TP order", position.getPositionId()));
        } else if (posInfo.stopOrdersNotActual()) {
          getLogger().info(String.format("Order container for base %s is not actual anymore", position.getPositionId()));
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn(String.format("Unsuccessful cancelling of safety orders for position %s",
                position.getPositionId()));
          finishPosition(posInfo, checkTime, "not-actual");
          notifyUser(prepareNotActualStopsMessage(position.getDirection(), posInfo.getPositionInfo().getPositionId()));
          return true;
        }
        //todo пока из третьего бота убраны страховочные, ветка неактуальна
        if (posInfo.newSafetyOrdersFilled(true)) {
          getLogger().debug(String.format("Some safety orders is filled. Average position price - %s", avgPrice));
          /*BigDecimal tpDiff = takeProfitOrder.getStopPrice().subtract(avgPrice);
          //Новый TP будет уже ближе к средней цене (можно поиграться с коэффициентом, 0.75->0.8->0.85)
          BigDecimal tpAbs = avgPrice.add(tpDiff.multiply(BigDecimal.valueOf(0.5f), MathContext.DECIMAL32));
          getLogger().debug(String.format("Rearranging %s (%s) position take-profit level. New value - %s",
              position.getPositionId(), position.getSymbol(), tpAbs));*/
          /*if (posInfo.rearrangeTakeProfit(getSignedClient(), ?) == null)
            getLogger().debug(String.format("Unsuccessful rearranging %s (%s) take-profit order",
                position.getPositionId(), position.getSymbol()));*/
        }
      }
    } else {
      notifyUser(String.format("%s. Unsuccessful open orders status update", this));
    }
    getLogger().info(getName() + ": " + posInfo.toString(lastRate == null ? null : (float) lastRate.getValue()));
    return false;
  }

  @Override
  public void saveOrdersState() {
  }

  @Override
  public void configureDefault() {
  }

  @Override
  public void customRuntimeReconfig() {
  }

  public boolean supportsHedgeMode() {
    return false;
  }

  protected void loadOpenedOrders() {
    String pair = getSymbol();
    /*longPosition = createAndLoadPositionContainer(DatabaseInteractor.loadOpenPosition(getId(), Consts.BUY), pair);
    shortPosition = createAndLoadPositionContainer(DatabaseInteractor.loadOpenPosition(getId(), Consts.SELL), pair);
    if (hasOpenedLongPositions())
      getLogger().info(getName() + ": " + getLongPosition().toString());
    if (hasOpenedShortPositions())
      getLogger().info(getName() + ": " + getShortPosition().toString());*/
  }

  protected abstract void loadExistingPositions();

  @Override
  public void setCurrencyPair(String baseCurrency, String quotedCurrency) {
    super.setCurrencyPair(baseCurrency, quotedCurrency);
    getLogger().info(String.format("Trader %s. Balances: %s/%s %s, %s/%s %s", getName(),
        baseCurrencyAmount, baseCurrencyTotalAmount, baseCurrency,
        quotedCurrencyAmount, quotedCurrencyTotalAmount, quotedCurrency));
    if (attachToExistingPositions())
      loadExistingPositions();
  }

  @Override
  public boolean notify(Map<String, Object> parameters) {
    boolean result = super.notify(parameters);
    if (parameters.containsKey(Consts.PARAMETER_APPC) && parameters.get(Consts.PARAMETER_APPC) instanceof Float) {
      lastAppc = (float) parameters.get(Consts.PARAMETER_APPC);
    }
    return result;
  }

  public abstract AbstractSignedClient getSignedClient();
}
