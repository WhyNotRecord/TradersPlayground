package ru.rexchange.trading.trader.futures;

import com.kucoin.futures.core.rest.response.OrderResponse;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.apis.kucoin.KucoinOrdersProcessor;
import ru.rexchange.data.Consts;
import ru.rexchange.gen.OrderInfoObject;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.KucoinSignedClient;

import java.util.List;

public class KucoinFuturesTrader extends CommonFuturesTrader<KucoinSignedClient> {
  protected static Logger LOGGER = LoggerFactory.getLogger(KucoinFuturesTrader.class);
  private KucoinSignedClient apiClient = null;

  public KucoinFuturesTrader(String name, AmountDeterminant amountDeterminant, float dealAmount) {
    super(name, amountDeterminant, dealAmount);
  }

  public KucoinFuturesTrader(TraderConfig trader) {
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
        baseCurrencyAmount = KucoinSignedClient.getFreeAssetBalance(baseCurrency, getSignedClient()).floatValue();
        baseCurrencyTotalAmount = KucoinSignedClient.getTotalAssetBalance(baseCurrency, getSignedClient()).floatValue();
      }
      if (quotedCurrency != null) {
        quotedCurrencyAmount = KucoinSignedClient.getFreeAssetBalance(quotedCurrency, getSignedClient()).floatValue();
        quotedCurrencyTotalAmount = KucoinSignedClient.getTotalAssetBalance(quotedCurrency, getSignedClient()).floatValue();
      }
    } catch (Exception e) {
      getLogger().warn("Error occurred while requesting free balance", e);
    }
  }

  /*protected float getRealDealAmount(float rate, float baseCurDealAmount) {
    return baseCurDealAmount  * rate / leverage;
  }*/

  protected boolean canTrade() {
    try {
      return getSignedClient().canTrade();
    } catch (Exception e) {
      getLogger().warn("Unsuccessful API request", e);
      return true;
    }
  }

  @Override
  public String setLeverage(int leverage) {
    String result = super.setLeverage(leverage);
    //если result == null, значит ничего не менялось
    if (result != null && baseCurrency != null) {
      try {
        // в момент задания параметров при первоначальном конфиге данных о торгуемой паре ещё нет,
        // так что leverage не передать на биржу
        // пара проставляется в момент привязки трейдера к боту
        KucoinOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);
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
        BinanceOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);*/
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
        BinanceOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);*/
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
      List<OrderResponse> orders = getSignedClient().getFilledOrders(symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        OrderResponse order = orders.get(i);
        result.append(order.toString()).append(System.lineSeparator());
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
      List<OrderResponse> orders = getSignedClient().getOrders(true, symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        OrderResponse order = orders.get(i);
        result.append(order.toString()).append(System.lineSeparator());
      }
      return result.toString();
    } catch (Exception e) {
      getLogger().warn("Error occurred while trying to load active orders for pair {}", symbol, e);
      return null;
    }
  }

  @Override
  protected AbstractOrdersProcessor<?, KucoinSignedClient> getOrdersProcessor() {
    return KucoinOrdersProcessor.getInstance(false);
  }

  @Override
  public String setParameter(String name, String value) {
    String parameterSet = super.setParameter(name, value);
    if (parameterSet == null);
    //здесь будут задаваться кастомные параметры

    return parameterSet;
  }

  @Override
  protected String findLastOpenedOrder(boolean buy, AbstractOrdersProcessor<?, KucoinSignedClient> processor) {
    try {
      List<OrderResponse> orders = getSignedClient().getFilledOrders(getSymbol());
      orders.sort((o1, o2) -> -o1.getOrderTime().compareTo(o2.getOrderTime()));
      String orderDirection = buy ? Consts.BUY : Consts.SELL;
      for (OrderResponse o : orders) {
        LOGGER.trace(o.toString());
        if (orderDirection.equals(o.getSide().toUpperCase()) && StringUtils.isEmpty(o.getStop()))
          return o.getClientOid();
      }
    } catch (Exception e) {
      getLogger().error("Unsuccessful orders request", e);
    }
    return null;
  }

  @Override
  public void setAuthInfo(TraderAuthenticator authenticator) {
    super.setAuthInfo(authenticator);
    if (!this.getAuthenticator().equals(authenticator)) {//todo никогда не вызывается?
      if (leverage != null && baseCurrency != null)
        KucoinOrdersProcessor.setLeverageSafe(apiClient, getSymbol(), leverage);
    }

    //В Kucoin, похоже, нет режимов позиций
    /* try {
      SignedClient apiClient = getSignedClient();
      if (!PositionMode.HEDGE.equals(apiClient.getPositionMode())) {
        apiClient.setPositionMode(true);
        getLogger().info(String.format("Hedge mode for trader %s set successfully", this));
      }
    if (getSignedClient().canWithdraw());// выдавать предупреждение
    } catch (Exception e) {
      if (e.getMessage().contains("No need to change position side")) {
        getLogger().info(e.getMessage());
      } else {
        getLogger().error(String.format("%s. Unsuccessful change to hedge position mode", this), e);
      }
    }*/
  }

  @NotNull
  protected KucoinOrdersProcessor.PositionContainer createCustomPositionContainer(PositionInfo positionInfo) {
    return new KucoinOrdersProcessor.PositionContainer(positionInfo);
  }

  protected void loadExistingPositions() {
    //todo здесь же в зависимости от спец. настройки запрашивать и подключать уже открытые сделки
    // но как определять входящие в состав позиции ордера?
    // Суммировать от самого последнего к более старым, пока не наберётся сумма из сведений о позиции?
    loadLastOpenOrders(5);
    //getSignedClient().getAccountData();
  }

  @Override
  protected void finishPosition(AbstractPositionContainer<KucoinSignedClient> position, Long closeTime, String initiator) {
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
  public KucoinSignedClient getSignedClient() {
    if (apiClient == null) {
      apiClient = new KucoinSignedClient(getAuthenticator());
    }

    return apiClient;
  }

}
