package ru.rexchange.trading.trader.futures;

import com.bybit.api.client.domain.position.PositionMode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.apis.bybit.BybitOrdersProcessor;
import ru.rexchange.data.Consts;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.BybitSignedClient;

import java.util.List;
import java.util.Map;

public class BybitFuturesTrader extends CommonFuturesTrader<BybitSignedClient> {
  protected static Logger LOGGER = LoggerFactory.getLogger(BybitFuturesTrader.class);
  private BybitSignedClient apiClient = null;

  public BybitFuturesTrader(String name, AmountDeterminant amountDeterminant, float dealAmount) {
    super(name, amountDeterminant, dealAmount);
  }

  public BybitFuturesTrader(TraderConfig trader) {
    super(trader);
  }

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  public boolean isRealTrader() {
    return true;
  }

  @Override
  public void requestCurrenciesAmount() {
    try {
      if (baseCurrency != null) {
        Float[] baseBalance = getSignedClient().getBalancesFloat(baseCurrency);
        if (baseBalance != null) {
          baseCurrencyAmount = baseBalance[0];
          baseCurrencyTotalAmount = baseBalance[1];
        }
      }
      if (quotedCurrency != null) {
        Float[] baseBalance = getSignedClient().getBalancesFloat(quotedCurrency);
        if (baseBalance != null) {
          quotedCurrencyAmount = baseBalance[0];
          quotedCurrencyTotalAmount = baseBalance[1];
        }
      }
    } catch (Exception e) {
      getLogger().warn("Error occurred while requesting free balance", e);
    }
  }

  protected boolean canTrade() {
    try {
      return getSignedClient().canTrade();
    } catch (Exception e) {
      getLogger().warn("Unsuccessful API request", e);
      return true;
    }
  }

  /*protected AbstractOrder getLastOpenedOrder(AbstractPositionContainer orderContainer) {
    if (orderContainer == null || orderContainer.getOrders().isEmpty())
      return null;
    int last = orderContainer.getOrders().size() - 1;
    return orderContainer.getOrders().get(last);
  }*/

  @Override
  public String setLeverage(int leverage) {
    String result = super.setLeverage(leverage);
    //если result == null, значит ничего не менялось
    if (result != null && baseCurrency != null) {
      try {
        // в момент задания параметров при первоначальном конфиге данных о торгуемой паре ещё нет,
        // так что leverage не передать на биржу
        // пара проставляется в момент привязки трейдера к боту
        BybitOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);
      } catch (Exception e) {
        getLogger().error("Unsuccessful leverage change", e);
      }
    }
    return result;
  }

  @Override
  public boolean preOpenBuy(DealInfo info) {
    try {
      /*if (leverage != null)
        BybitOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);*/
      //TelegramMessenger.getInstance(tgChatId).sendToTelegram(preparePreDealNotificationMessage(desiredRate, true));
    } catch (Exception e) {
      getLogger().warn("Error occurred while preparing to open BUY order", e);
      return false;
    }
    return true;
  }

  @Override
  public boolean preOpenSell(DealInfo info) {
    try {
      /*if (leverage != null)
        BybitOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);*/
      //TelegramMessenger.getInstance(tgChatId).sendToTelegram(preparePreDealNotificationMessage(desiredRate, false));
    } catch (Exception e) {
      getLogger().warn("Error occurred while preparing to open BUY order", e);
      return false;
    }
    return false;
  }

  /**
   * Возвращает список последних исполненных ордеров (статус FILLED)
   * @param count - количество запрашиваемых ордеров
   */
  protected String loadLastOpenOrders(int count) {
    if (count <= 0)//todo привести к единому формату для всех трейдеров
      return null;
    if (baseCurrency == null || quotedCurrency == null) {
      getLogger().warn("Can't load last orders: no data about currency pair");
      return null;
    }
    String symbol = getSymbol();
    StringBuilder result = new StringBuilder();
    try {
      List<BybitOrder> orders = getSignedClient().getOrders(true, symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        BybitOrder order = orders.get(i);
        if ("filled".equalsIgnoreCase(order.getString("orderStatus")))
          result.append(order).append(System.lineSeparator());
      }
      return result.toString();
    } catch (Exception e) {
      getLogger().warn("Error occurred while trying to load last orders for pair {}", symbol, e);
      return null;
    }
  }

  /**
   * Возвращает список текущих открытых, но не выполненных ордеров (статус NEW)
   * @param count - количество запрашиваемых ордеров
   */
  protected String loadActiveOrders(int count) {
    if (count <= 0)
      return null;
    String symbol = getSymbol();
    StringBuilder result = new StringBuilder();
    try {
      List<BybitOrder> orders = getSignedClient().getOrders(true, symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        BybitOrder order = orders.get(i);
        result.append(order.toString()).append(System.lineSeparator());
      }
      return result.toString();
    } catch (Exception e) {
      getLogger().warn("Error occurred while trying to load active orders for pair {}", symbol, e);
      return null;
    }
  }

  @Override
  protected AbstractOrdersProcessor<?, BybitSignedClient> getOrdersProcessor() {
    return BybitOrdersProcessor.getInstance(false);
  }

  @Override
  public boolean supportsHedgeMode() {
    try {
      return PositionMode.BOTH_SIDES.equals(getSignedClient().getPositionMode(getSymbol()));
    } catch (Exception e) {
      LOGGER.warn("Error occurred trying to find out position mode for trader {}", this, e);
      return false;
    }
  }

  @Override
  public String setParameter(String name, String value) {
    String parameterSet = super.setParameter(name, value);
    if (parameterSet == null);
    //здесь будут задаваться кастомные параметры
    return parameterSet;
  }

  @Override
  protected String findLastOpenedOrder(boolean buy, AbstractOrdersProcessor<?, BybitSignedClient> processor) {
    try {
      List<BybitOrder> orders = getSignedClient().getOrders(false, getSymbol());
      String orderDirection = buy ? Consts.BUY : Consts.SELL;
      for (BybitOrder o : orders) {
        if (orderDirection.equalsIgnoreCase(o.getString("side")))
          return o.getString(BybitSignedClient.FIELD_ORDER_ID);
      }
    } catch (Exception e) {
      getLogger().error("Unsuccessful orders request", e);
      return "Unsuccessful request";
    }
    return null;
  }

  @Override
  public void setAuthInfo(TraderAuthenticator authenticator) {
    super.setAuthInfo(authenticator);
    if (!this.getAuthenticator().equals(authenticator)) {
      try {
        BybitSignedClient apiClient = getSignedClient();
        if (!PositionMode.BOTH_SIDES.equals(apiClient.getPositionMode(getSymbol()))) {
          apiClient.setPositionMode(getSymbol(), true);
          getLogger().info("Hedge mode for trader {} set successfully", this);
        }
        if (leverage != null && baseCurrency != null)
          BybitOrdersProcessor.setLeverage(apiClient, getSymbol(), leverage);
        if (apiClient.canWithdraw())
          notifyUser("You should disable withdrawing ability in your API key settings!");
      } catch (Exception e) {
        if (e.getMessage().contains("No need to change position side")) {
          getLogger().info(e.getMessage());
        } else {
          getLogger().error("{}. Unsuccessful change to hedge position mode", this, e);
        }
      }
    }
  }

  @NotNull
  protected BybitOrdersProcessor.PositionContainer createCustomPositionContainer(PositionInfo positionInfo) {
    return new BybitOrdersProcessor.PositionContainer(positionInfo);
  }

  protected void loadExistingPositions() {
    loadLastOpenOrders(5);
    //getSignedClient().getAccountData();
  }

  @Override
  protected void finishPosition(AbstractPositionContainer<BybitSignedClient> position, Long closeTime, String initiator) {
    super.finishPosition(position, closeTime, initiator);
    if (STOP_LOSS_PARAM.equals(initiator)) {
      OrderInfoObject tpOrder = position.getTakeProfitOrder();
      if (tpOrder != null) {
        if (!getOrdersProcessor().cancelOrder(getSignedClient(), tpOrder.getOrderId(), tpOrder.getSymbol())) {
          getLogger().warn("Unsuccessful TP cancellation for position {}", position.getPositionInfo().getPositionId());
        }
      }
    } else if (TAKE_PROFIT_PARAM.equals(initiator)) {
      OrderInfoObject slOrder = position.getStopLossOrder();
      if (slOrder != null) {
        if (!getOrdersProcessor().cancelOrder(getSignedClient(), slOrder.getOrderId(), slOrder.getSymbol())) {
          getLogger().warn("Unsuccessful SL cancellation for position {}", position.getPositionInfo().getPositionId());
        }
      }
    }
  }

  @Override
  public BybitSignedClient getSignedClient() {
    if (apiClient == null) {
      apiClient = new BybitSignedClient(getAuthenticator());
    }
    return apiClient;
  }
}
