package ru.rexchange.trading.trader.futures;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.Consts;
import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.exception.UserException;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.PositionInfoObject;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.tools.*;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.AbstractSignedClient;
import ru.rexchange.trading.trader.AbstractTrader;

import java.math.BigDecimal;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

public abstract class CommonFuturesTrader<C extends AbstractSignedClient> extends AbstractTrader {
  protected static Logger LOGGER = LoggerFactory.getLogger(CommonFuturesTrader.class);
  private static final Integer DEFAULT_LEVERAGE_VALUE = 2;
  public static final String STOP_LOSS_PARAM = "stop-loss";
  public static final String TAKE_PROFIT_PARAM = "take-profit";
  @Getter
  protected AbstractPositionContainer<C> longPosition = null;
  @Getter
  protected AbstractPositionContainer<C> shortPosition = null;
  protected Integer leverage = null;
  private long newOrderLivePeriod = 5 * 60 * 1000L;
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
  public AbstractPositionContainer<C> openBuy(DealInfo info) {
    float desiredRate = info.getDealRate();
    Float stopLoss = info.getStopLoss(), takeProfit = info.getTakeProfit();
    getLogger().info(prepareOpenDealMessage(Consts.BUY, desiredRate, stopLoss, takeProfit));
    double dealAmount = getDealAmount(desiredRate, true);
    AbstractPositionContainer<C> position = null;
    try {
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
      LoggingUtils.logError(getLogger(), e,"Error occurred while opening BUY order by " + this);
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
    getLogger().info("Opening market BUY deal on {}", getSymbol());
    AbstractPositionContainer<C> position = null;
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
      LoggingUtils.logError(getLogger(), e,"Error occurred while opening BUY order by " + this);
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
  public AbstractPositionContainer<C> openSell(DealInfo info) {
    float desiredRate = info.getDealRate();
    Float stopLoss = info.getStopLoss(), takeProfit = info.getTakeProfit();
    getLogger().info(prepareOpenDealMessage(Consts.SELL, desiredRate, stopLoss, takeProfit));
    double dealAmount = getDealAmount(desiredRate, false);
    AbstractPositionContainer<C> position = null;
    try {
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
      LoggingUtils.logError(getLogger(), e, "Error occurred while opening SELL order by " + this);
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
    getLogger().info("Opening market SELL deal on {}", getSymbol());
    AbstractPositionContainer<C> position = null;
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
      LoggingUtils.logError(getLogger(), e, "Error occurred while opening SELL order by " + this);
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

  //@Override
  public OrderInfoObject limitBuy(DealInfo info) {
    float desiredRate = info.getDealRate();
    String symbol = getSymbol();
    getLogger().info("Posting limit BUY order on {}: {}", symbol, desiredRate);
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
      LoggingUtils.logError(getLogger(), e, "Error occurred while posting limit BUY order by " + this);
      notifyUser(prepareOrderFailedNotificationMessage(e.getMessage(), true));
    }

    return order;
  }

  //@Override
  public OrderInfoObject limitSell(DealInfo info) {
    float desiredRate = info.getDealRate();
    String symbol = getSymbol();
    getLogger().info("Posting limit SELL order on {}: {}", symbol, desiredRate);
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
      LoggingUtils.logError(getLogger(), e, "Error occurred while posting limit SELL order by " + this);
      notifyUser(prepareOrderFailedNotificationMessage(e.getMessage(), false));
    }

    return order;
  }

  @Override
  public boolean closeBuy(DealInfo info) {
    if (hasOpenedLongPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      getLogger().info("{}. Closing BUY deal on {} by request ({}) at {}", getName(), getSymbol(), strategy, closingRate);
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      try {
        getLongPosition().update(getSignedClient());
        if (!getLongPosition().getPositionInfo().onStatusOpen()) {
          getLogger().info("{}. Long position isn't open, quitting...", getName());
          return false;
        }
      } catch (Exception e) {
        getLogger().warn("Error occurred while updating long position", e);
      }
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
        notifyUser(prepareDealClosedNotificationMessage("BUY", closeOrder.getPrice(), closeTime, profit, strategy));
        longPosition = null;
        return true;
      } else {
      }
    }
    return false;
  }

  public boolean closeBuyPartially(DealInfo info) {
    if (hasOpenedLongPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      Integer count = info != null ? (Integer) info.getParameter(DealInfo.PARAM_ORDERS_COUNT) : 1;
      if (count <= 0) {
        getLogger().info("{}. Unexpected parameter for partial closing, quitting...", getName());
      }
      getLogger().info("{}. Closing {} BUY order(s) on {} by request ({}) at {}",
          getName(), count, getSymbol(), strategy, closingRate);
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      try {
        getLongPosition().update(getSignedClient());
        if (!getLongPosition().getPositionInfo().onStatusOpen()) {
          getLogger().info("{}. Long position isn't open, quitting...", getName());
          return false;
        }
      } catch (Exception e) {
        getLogger().warn("Error occurred while updating long position", e);
      }
      getLongPosition().cancelSafetyOrders(getSignedClient());
      BigDecimal amount = getLongPosition().getLastOrdersWeight(count);
      OrderInfoObject closeOrder = getLongPosition().closePartially(getSignedClient(), amount);
      if (closeOrder != null) {
        long closeTime = closeOrder.getOrderTimestamp().getTime();
        notifyUser(preparePositionPartiallyClosedNotificationMessage("BUY", closeOrder.getPrice(), closeTime, strategy));
        return true;
      } else {
        getLogger().warn("{}. Unsuccessful partial long position closing", getName());
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean closeSell(DealInfo info) {
    if (hasOpenedShortPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      getLogger().info("{}. Closing SELL deal on {} by request ({}) at {}", getName(), getSymbol(), strategy, closingRate);
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      try {
        getShortPosition().update(getSignedClient());
        if (!getShortPosition().getPositionInfo().onStatusOpen()) {
          getLogger().info("{}. Short position isn't open, quitting...", getName());
          return false;
        }
      } catch (Exception e) {
        getLogger().warn("Error occurred while updating short position", e);
      }
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
        notifyUser(prepareDealClosedNotificationMessage("SELL", closeOrder.getPrice(), closeTime, profit, strategy));
        shortPosition = null;
        return true;
      } else {
      }
    }
    return false;
  }

  public boolean closeSellPartially(DealInfo info) {
    if (hasOpenedShortPositions()) {
      float closingRate = info != null ? info.getDealRate() : 0.f;
      String strategy = info != null ? String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY)) : "unknown";
      Integer count = info != null ? (Integer) info.getParameter(DealInfo.PARAM_ORDERS_COUNT) : 1;
      if (count == null || count <= 0) {
        getLogger().info("{}. Unexpected parameter for partial closing, quitting...", getName());
      }
      getLogger().info("{}. Closing {} SELL order(s) on {} by request ({}) at {}",
          getName(), count, getSymbol(), strategy, closingRate);
      //Обновляем позицию, чтобы точно знать выполненный объём ордера, а также проверить, не закрылась ли она уже
      try {
        getShortPosition().update(getSignedClient());
        if (!getShortPosition().getPositionInfo().onStatusOpen()) {
          getLogger().info("{}. Short position isn't open, quitting...", getName());
          return false;
        }
      } catch (Exception e) {
        getLogger().warn("Error occurred while updating short position", e);
      }
      getShortPosition().cancelSafetyOrders(getSignedClient());
      BigDecimal amount = getShortPosition().getLastOrdersWeight(count);
      OrderInfoObject closeOrder = getShortPosition().closePartially(getSignedClient(), amount);
      if (closeOrder != null) {
        long closeTime = closeOrder.getOrderTimestamp().getTime();
        notifyUser(preparePositionPartiallyClosedNotificationMessage("SELL", closeOrder.getPrice(), closeTime, strategy));
        return true;
      } else {
        getLogger().warn("{}. Unsuccessful partial short position closing", getName());
        return false;
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

  private boolean changeOrderStopLevel(DealInfo info, AbstractPositionContainer<C> position) {
    getLogger().debug("{}. changeOrderStopLevel, DealInfo: {}", this, info);
    if (position == null) {
      getLogger().warn("Trying to adjust stop-orders for non-existing position on {}", getSymbol());
      return false;
    }
    boolean result = false;
    StringBuilder sb = new StringBuilder();
    float delta = 0.001f;
    if (info.getStopLoss() != null) {
      String rejectCause = checkStopLoss(info, position, delta);
      if (rejectCause == null) {
        if (position.rearrangeStopLoss(getSignedClient(), BigDecimal.valueOf(info.getStopLoss())) != null) {
          sb.append(prepareStopChangedNotificationMessage(position.getSide(), "Stop-loss",
              info.getStopLoss(), position.getExpectedProfit(position.getStopLossOrder().getPrice()),
              String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
          result = true;
        } else {
          sb.append(prepareStopNotChangedNotificationMessage(position.getSide(), "Stop-loss", info.getStopLoss(),
              String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
        }
      } else {
        getLogger().debug("{} {}", this, rejectCause);
      }
    }
    if (info.getTakeProfit() != null) {
      String rejectCause = checkTakeProfit(info, position, delta);
      if (rejectCause == null) {
        if (position.rearrangeTakeProfit(getSignedClient(), BigDecimal.valueOf(info.getTakeProfit())) ||
            info.getTakeProfit() == 0) {
          if (!sb.isEmpty())
            sb.append(System.lineSeparator());
          if (position.getTakeProfitOrder() != null) {
            sb.append(prepareStopChangedNotificationMessage(position.getSide(), "Take-profit",
                info.getTakeProfit(), position.getExpectedProfit(position.getTakeProfitOrder().getPrice()),
                String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
          } else {
            sb.append(prepareStopRemovedNotificationMessage(position.getSide(), "Take-profit",
                String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
          }
          result = true;
        } else {
          sb.append(prepareStopNotChangedNotificationMessage(position.getSide(), "Take-profit", info.getStopLoss(),
              String.valueOf(info.getParameter(DealInfo.PARAM_STRATEGY))));
        }
      } else {
        getLogger().debug("{} {}", this, rejectCause);
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
    double result = super.getDealAmount(rate, buy);
    /*if (getSafetyOrdersAmountAddedPercent() > 0.f || getSafetyOrdersAdditionFactor() != null) {
      // увеличиваем размер только для страховочных ордеров (когда позиция в убытке)
      if (buy && hasOpenedLongPositions() && getCurrentLongProfitability(rate) < 0f) {
        float safetyOrderAmountMultiplicator = evaluateSafetyOrderAmountFactor(getLongPosition(), rate);
        result = getLongPosition().getFirstOrder().getAmount() * safetyOrderAmountMultiplicator;
      } else if (!buy && hasOpenedShortPositions() && getCurrentShortProfitability(rate) < 0f) {
        float safetyOrderAmountMultiplicator = evaluateSafetyOrderAmountFactor(getShortPosition(), rate);
        result = getShortPosition().getFirstOrder().getAmount() * safetyOrderAmountMultiplicator;
      }
    }*/
    float maxDealAmount = getDealMarginLimit() * getLeverage();
    if (result * rate > maxDealAmount) {
      notifyAboutDealMarginLimitReaching(rate, result);
      return maxDealAmount / rate;
    }
    return result;
  }

  private float getDealMarginLimit() {
    return 100f;
  }

  protected void notifyAboutDealMarginLimitReaching(float rate, double result) {
    getLogger().info("{}. User's plan doesn't allow so big deals - {} {}", this, result * rate, quotedCurrency);
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

  protected void accountProfit(float profit, AbstractPositionContainer<C> position) {
    quotedCurrencyAmount += profit;
    quotedCurrencyTotalAmount += profit;
    position.getPositionInfo().setProfit(profit);
    //BigDecimal freeFunds = position.getExecutedAmount().multiply(position.getAvgPrice());
    //quotedCurrencyAmount += freeFunds.floatValue() / leverage;
  }

  protected void finishPosition(AbstractPositionContainer<C> position, Long closeTime, String initiator) {
    PositionInfoObject posInfo = position.getPositionInfo();
    posInfo.setCloseTimestamp(new Timestamp(closeTime));
    posInfo.setStatus(PositionInfoObject.PositionStatus.CLOSED.name());
    posInfo.setComment(posInfo.getComment() + ":" + initiator);
  }

  protected void cancelPosition(AbstractPositionContainer<C> position, Long closeTime, String initiator) {
    PositionInfoObject posInfo = position.getPositionInfo();
    posInfo.setCloseTimestamp(new Timestamp(closeTime));
    posInfo.setStatus(PositionInfoObject.PositionStatus.CANCELLED.name());
    posInfo.setComment(posInfo.getComment() + ":" + initiator);
  }

  @Override
  public String setParameter(String name, String value) {
    return null;
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
      if (getLongPosition() == null) {
        longPosition = getOrdersProcessor().createPositionContainer(order);
        getLongPosition().getPositionInfo().setComment("Manual");
        //longPosition.setParameter(AbstractPositionContainer.PARAM_COMMENT, "Manual");
        dealOpened();
      } else {
        for (OrderInfoObject o : getLongPosition().getOrders()) {
          if (o.equals(order)) {
            getLogger().warn("Order container already contains long order {} on {}", orderId, symbol);
            return false;
          }
        }
        getLongPosition().addOrder(order);
        getLongPosition().incrementSafetyOrdersTriggered();
      }
      checkOpenedPosition(getLongPosition(), null);
      return true;
    } catch (Exception e) {
      getLogger().warn("Error occurred while querying order with id {}", orderId, e);
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
      if (getShortPosition() == null) {
        shortPosition = getOrdersProcessor().createPositionContainer(order);
        getShortPosition().getPositionInfo().setComment("Manual");
        //shortPosition.setParameter(AbstractPositionContainer.PARAM_COMMENT, "Manual");
        dealOpened();
      } else {
        for (OrderInfoObject o : getShortPosition().getOrders()) {
          if (o.equals(order)) {
            getLogger().warn("Order container already contains short order {} on {}", orderId, symbol);
            return false;
          }
        }
        getShortPosition().addOrder(order);
        getShortPosition().incrementSafetyOrdersTriggered();
      }
      checkOpenedPosition(getShortPosition(), null);
      return true;
    } catch (Exception e) {
      getLogger().warn("Error occurred while querying order with id {}", orderId, e);
      return false;
    }
  }

  protected abstract String findLastOpenedOrder(boolean buy, AbstractOrdersProcessor<?, C> processor);

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
      getLogger().warn("{}. Leverage is not set. Using default value - " + DEFAULT_LEVERAGE_VALUE, this);
      return DEFAULT_LEVERAGE_VALUE;
    }
    return leverage;
  }

  protected long getOrderLivePeriod(AbstractPositionContainer<C> posInfo) {
    if (posInfo.hasParameter(DealInfo.PARAM_MANUAL_LIMIT))
      return TimeUtils.HOUR_IN_MS * 24L;
    return newOrderLivePeriod;
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
  public float getSummaryProfit(float rate) {
    float profit = 0.f;
    if (hasOpenedLongPositions()) {
      profit += getLongPosition().getExpectedProfit(rate);
    }
    if (hasOpenedShortPositions()) {
      profit += getShortPosition().getExpectedProfit(rate);
    }
    return profit;
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

  private DealInfo prepareOpenPositionInfo(AbstractPositionContainer<C> position) {
    if (position == null)
      return null;
    DealInfo result = new DealInfo(position.getPositionInfo().getAveragePrice(),
        position.getStopLossOrder() == null ? null :position.getStopLossOrder().getPrice(),
        position.getTakeProfitOrder() == null ? null : position.getTakeProfitOrder().getPrice());
    result.addParameter(DealInfo.PARAM_POSITION_WEIGHT, position.getPositionWeight());
    result.addParameter(DealInfo.PARAM_ORDERS_COUNT, position.getOrders().size());
    if (position.getLastOrder() != null)
      result.addParameter(DealInfo.PARAM_LAST_ORDER_TIME, position.getLastOrder().getOrderTimestamp().getTime());
    if (position.getPositionInfo().getOpenTimestamp() != null)
      result.addParameter(DealInfo.PARAM_POSITION_TIME, position.getPositionInfo().getOpenTimestamp().getTime());
    return result;
  }

  private DealInfo prepareLimitOrderDealInfo(float price) {
    DealInfo dealInfo = new DealInfo(price, (Float) null, null);
    dealInfo.addParameter(DealInfo.PARAM_MANUAL_LIMIT, Boolean.TRUE);
    return dealInfo;
  }

  public String getSymbol() {
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

  private float getFutureAvgPrice(AbstractPositionContainer<C> position, float dealRate) {
    int ordersCount = position.getTriggeredOrdersCount();
    float result = (position.getPositionInfo().getAveragePrice() * ordersCount + dealRate) / (ordersCount + 1);
    getLogger().debug("Future estimated avg position rate evaluated - {}", result);
    return result;
  }

  protected abstract AbstractOrdersProcessor<?, C> getOrdersProcessor();

  /**
   *
   * @param lastRate - current rate
   * @return true if trader has a position
   */
  @Override
  public boolean checkOpenedPositions(AbstractRate<? extends Number> lastRate) {
    try {
      if (hasOpenedLongPositions()) {
        if (checkOpenedPosition(getLongPosition(), lastRate)) {
          longPosition = null;
        }
      }
    } catch (SocketException | UnknownHostException e) {
      LoggingUtils.logError(getLogger(), e, "Temporary error while processing BUY position of " + getName());
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Processing LONG position error " + getName());
    }
    try {
      if (hasOpenedShortPositions()) {
        if (checkOpenedPosition(getShortPosition(), lastRate)) {
          shortPosition = null;
        }
      }
    } catch (SocketException | UnknownHostException e) {
      LoggingUtils.logError(getLogger(), e, "Temporary error while processing SELL position of " + getName());
    } catch (Exception e) {
      LoggingUtils.logError(getLogger(), e, "Processing SHORT position error " + getName());
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
  protected boolean checkOpenedPosition(@NotNull AbstractPositionContainer<C> posInfo, @Nullable AbstractRate<? extends Number> lastRate)
    throws Exception {
    boolean success = posInfo.update(getSignedClient()) > 0;
    if (success) {
      long checkTime = lastRate != null ? lastRate.getCloseTime() : DateUtils.currentTimeMillis();
      boolean positionIsOpen = posInfo.someOrdersFilled();
      PositionInfo position = posInfo.getPositionInfo();
      if (!positionIsOpen) {
        if (checkTime - position.getOpenTimestamp().getTime() > getOrderLivePeriod(posInfo)) {
          int cancelled = posInfo.cancel(getSignedClient());
          if (cancelled > 0) {
            cancelPosition(posInfo, checkTime, "unfired");
            getLogger().info("{} order on {} is not filled and cancelled", position.getDirection(), position.getSymbol());
            if (cancelled < 3)
              getLogger().info("Some of the additional orders could be left uncanceled, cancelled {} order(s)", cancelled);
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
          getLogger().debug("{} position on {} closed by stop-loss at {}", position.getDirection(), position.getSymbol(), stopPrice);
          long closeTime = stopLossOrder.getOrderTimestamp().getTime();
          finishPosition(posInfo, closeTime, STOP_LOSS_PARAM);
          BigDecimal executedAmount = posInfo.getExecutedAmount();//вычисляем до отмены страховочных
          float profit = evaluateProfit(avgPrice.floatValue(), stopPrice, executedAmount, position.getDirection());
          LoggingUtils.logDealCloseInfo(this.toString(),
              position.getOpenTimestamp().getTime(), position.getCloseTimestamp().getTime(),
              baseCurrency, quotedCurrency, avgPrice.floatValue(), stopPrice,
              executedAmount, position.getDirection(), profit,
              posInfo.getPositionInfo().getComment(), BinanceFuturesTrader.LOGGER);
          accountProfit(profit, posInfo);
          notifyUser(prepareDealClosedNotificationMessage(position.getDirection(), stopPrice, closeTime, profit, STOP_LOSS_PARAM));
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn("Unsuccessful cancelling of safety orders for position {}", position.getPositionId());
          return true;
        } else if (takeProfitOrder != null && OrderInfoObject.OrderStatus.FILLED.equals(takeProfitOrder.getStatus())) {
          float stopPrice = takeProfitOrder.getAvgPrice().floatValue();
          getLogger().debug("{} position on {} closed by take-profit at {}", position.getDirection(), position.getSymbol(), stopPrice);
          long closeTime = takeProfitOrder.getOrderTimestamp().getTime();
          finishPosition(posInfo, closeTime, TAKE_PROFIT_PARAM);
          BigDecimal executedAmount = posInfo.getExecutedAmount();//вычисляем до отмены страховочных
          float profit = evaluateProfit(avgPrice.floatValue(), stopPrice, executedAmount, position.getDirection());
          LoggingUtils.logDealCloseInfo(this.toString(),
              position.getOpenTimestamp().getTime(), position.getCloseTimestamp().getTime(),
              baseCurrency, quotedCurrency, avgPrice.floatValue(), stopPrice,
              executedAmount, position.getDirection(), profit,
              posInfo.getPositionInfo().getComment(), BinanceFuturesTrader.LOGGER);
          accountProfit(profit, posInfo);
          notifyUser(prepareDealClosedNotificationMessage(position.getDirection(), stopPrice, closeTime, profit, TAKE_PROFIT_PARAM));
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn("Unsuccessful cancelling of safety orders for position {}", position.getPositionId());
          return true;
        } else if (posInfo.getStopLossOrder() == null && posInfo.getTakeProfitOrder() == null) {
          getLogger().warn("Order container for base {} has no SL nor TP order", position.getPositionId());
        } else if (posInfo.stopOrdersNotActual()) {
          getLogger().info("Order container for base {} is not actual anymore", position.getPositionId());
          if (!posInfo.cancelSafetyOrders(getSignedClient()))
            getLogger().warn("Unsuccessful cancelling of safety orders for position {}", position.getPositionId());
          finishPosition(posInfo, checkTime, "not-actual");
          notifyUser(prepareNotActualStopsMessage(position.getDirection(), posInfo.getPositionInfo().getPositionId()));
          return true;
        }
      }
    } else {
      notifyUser(String.format("Unsuccessful open orders status update for %s position", posInfo.getSide()));
    }
    getLogger().info("{}: {}", getName(), posInfo.toString(lastRate == null ? null : (float) lastRate.getValue()));
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

  @NotNull
  protected abstract AbstractPositionContainer<C> createCustomPositionContainer(PositionInfo positionInfo);

  protected abstract void loadExistingPositions();

  @Override
  public void setCurrencyPair(String baseCurrency, String quotedCurrency) {
    super.setCurrencyPair(baseCurrency, quotedCurrency);
    getLogger().info("Trader {}. Balances: {}/{} {}, {}/{} {}",
        getName(), baseCurrencyAmount, baseCurrencyTotalAmount, baseCurrency,
        quotedCurrencyAmount, quotedCurrencyTotalAmount, quotedCurrency);
  }

  @Override
  public boolean notify(Map<String, Object> parameters) {
    boolean result = super.notify(parameters);
    if (parameters.containsKey(Consts.PARAMETER_APPC) && parameters.get(Consts.PARAMETER_APPC) instanceof Float) {
      lastAppc = (float) parameters.get(Consts.PARAMETER_APPC);
    }
    return result;
  }

  public abstract C getSignedClient();
}
