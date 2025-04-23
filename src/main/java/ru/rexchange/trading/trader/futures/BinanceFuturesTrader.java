package ru.rexchange.trading.trader.futures;

import binance.futures.enums.PositionMode;
import binance.futures.model.AccountBalance;
import binance.futures.model.Order;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.apis.binance.BinanceFuturesApiProvider;
import ru.rexchange.apis.binance.BinanceOrdersProcessor;
import ru.rexchange.data.Consts;
import ru.rexchange.gen.PositionInfo;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.trading.AbstractOrdersProcessor;
import ru.rexchange.trading.TraderAuthenticator;
import ru.rexchange.trading.trader.AbstractPositionContainer;
import ru.rexchange.trading.trader.BinanceSignedClient;

import java.util.List;

public class BinanceFuturesTrader extends CommonFuturesTrader {
  protected static Logger LOGGER = LoggerFactory.getLogger(BinanceFuturesTrader.class);
  private BinanceSignedClient apiClient = null;

  public BinanceFuturesTrader(String name, AmountDeterminant amountDeterminant, float dealAmount) {
    super(name, amountDeterminant, dealAmount);
  }

  public BinanceFuturesTrader(TraderConfig trader) {
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
      if (baseCurrency != null) {//todo При открытии сделок вычитать
        AccountBalance baseAssetBalance = BinanceFuturesApiProvider.getAssetBalance(baseCurrency, getSignedClient());
        if (baseAssetBalance != null) {
          baseCurrencyAmount = baseAssetBalance.getAvailableBalance().floatValue();
          baseCurrencyTotalAmount = baseAssetBalance.getBalance().floatValue();
        }
      }
      if (quotedCurrency != null) {
        AccountBalance quotedAssetBalance = BinanceFuturesApiProvider.getAssetBalance(quotedCurrency, getSignedClient());
        if (quotedAssetBalance != null) {
          quotedCurrencyAmount = quotedAssetBalance.getAvailableBalance().floatValue();
          quotedCurrencyTotalAmount = quotedAssetBalance.getBalance().floatValue();
        }
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
      return BinanceFuturesApiProvider.canTrade(getSignedClient());
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
        //todo в момент первоначального конфига данных о торгуемой паре ещё нет, так что leverage не передать на биржу
        // пара проставляется в момент привязки трейдера к боту
        BinanceOrdersProcessor.setLeverage(getSignedClient(), getSymbol(), leverage);
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
      List<Order> orders = getSignedClient().getFilledOrders(symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        Order order = orders.get(i);
        result.append(order.toString()).append(System.lineSeparator());
      }
      return result.toString();
    } catch (Exception e) {
      getLogger().warn("Error occurred while trying to load last orders for pair " + symbol, e);
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
      List<Order> orders = getSignedClient().getOrders(true, symbol);
      for (int i = Math.max(0, orders.size() - count); i < orders.size(); i++) {
        Order order = orders.get(i);
        result.append(order.toString()).append(System.lineSeparator());
      }
      return result.toString();
    } catch (Exception e) {
      getLogger().warn("Error occurred while trying to load active orders for pair " + symbol, e);
      return null;
    }
  }

  @Override
  protected AbstractOrdersProcessor getOrdersProcessor() {
    return BinanceOrdersProcessor.getInstance(false);
  }

  @Override
  public boolean supportsHedgeMode() {
    try {
      return PositionMode.HEDGE.equals(getSignedClient().getPositionMode());
    } catch (Exception e) {
      LOGGER.warn("Error occurred trying to find out position mode for trader " + this, e);
      return false;
    }
  }

  @Override
  public String setParameter(String name, String value) {
    String parameterSet = super.setParameter(name, value);
    if (parameterSet == null);//todo здесь будут задаваться кастомные параметры

    return parameterSet;
  }

  @Override
  protected String findLastOpenedOrder(boolean buy, AbstractOrdersProcessor processor) {
    try {
      List<Order> orders = getSignedClient().getFilledOrders(getSymbol());
      orders.sort((o1, o2) -> -o1.getUpdateTime().compareTo(o2.getUpdateTime()));
      String orderDirection = buy ? Consts.BUY : Consts.SELL;
      for (Order o : orders) {
        LOGGER.trace(o.toString());
        if (orderDirection.equals(o.getSide().toUpperCase())/*todo check type=MARKET/LIMIT*/)
          return o.getClientOrderId();
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
    if (!this.getAuthenticator().equals(authenticator)) {//todo никогда не вызывается?
      try {
        BinanceSignedClient apiClient = getSignedClient();
        if (!PositionMode.HEDGE.equals(apiClient.getPositionMode())) {
          apiClient.setPositionMode(true);
          getLogger().info("Hedge mode for trader {} set successfully", this);
        }
        if (leverage != null && baseCurrency != null)
          BinanceOrdersProcessor.setLeverage(apiClient, getSymbol(), leverage);
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
  protected AbstractPositionContainer createCustomPositionContainer(PositionInfo positionInfo) {
    return new BinanceOrdersProcessor.PositionContainer(positionInfo);
  }

  protected void loadExistingPositions() {
    //todo здесь же в зависимости от спец. настройки запрашивать и подключать уже открытые сделки
    // но как определять входящие в состав позиции ордера?
    // Суммировать от самого последнего к более старым, пока не наберётся сумма из сведений о позиции?
    loadLastOpenOrders(5);
    //getSignedClient().getAccountData();
  }

  @Override
  public BinanceSignedClient getSignedClient() {
    if (apiClient == null) {
      apiClient = new BinanceSignedClient(getAuthenticator());
    }
    return apiClient;
  }

}
