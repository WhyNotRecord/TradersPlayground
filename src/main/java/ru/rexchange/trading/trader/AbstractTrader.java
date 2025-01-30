package ru.rexchange.trading.trader;

import binance.futures.enums.OrderSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.data.Consts;
import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.gen.AppUser;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.trading.TradeTools;
import ru.rexchange.trading.TraderAuthenticator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.util.*;

public abstract class AbstractTrader {
  protected static Logger LOGGER = LoggerFactory.getLogger(AbstractTrader.class);
	public static final String DEFAULT_TRADER = "default";

	protected String name = DEFAULT_TRADER;
	protected String id = null;

	//TODO трейдеру (фьючей) необязательно хранить каждую валюту
	// для ордеров можно передавать в DealInfo
	// при загрузке состояния можно передавать в параметрах при вызове из бота
	// при сохранении состояния использовать данные из самого ордера (можно хранить в виде символа пары)
	protected String baseCurrency;
	protected String quotedCurrency;
	protected float baseCurrencyAmount = 0f;
	protected float quotedCurrencyAmount = 0f;
	protected float baseCurrencyTotalAmount = 0f;
	protected float quotedCurrencyTotalAmount = 0f;
	protected AmountDeterminant amountDeterminant;
	protected double amountValue = 10.0;
	protected float freeFundsDealCoefficient = 0.5f;
	protected float freeFundsMarginCoefficient = 0.25f;//todo определять в зависимости от риска
	protected String tgChatId = null;
	protected boolean allowFartherStopLoss = false;
	protected boolean allowUnprofitableTakeProfit = false;
	private TraderAuthenticator authenticator;
	protected boolean openByMarket = false;
	protected final Set<ActionListener> listeners = new HashSet<>();

	public static float evaluateProfit(float openRate, float closeRate, BigDecimal amount, String side) {
		float diff = closeRate * amount.floatValue() - openRate * amount.floatValue();
		if (!OrderSide.BUY.name().equalsIgnoreCase(side))
			diff *= -1;
		return diff;
	}

	public enum AmountDeterminant {
		FIXED_BASE,
		PERCENT_BASE,
		FIXED_QUOTED,
		PERCENT_QUOTED;

		public static AmountDeterminant fromInt(int value) {
			// Предполагаем, что value соответствует порядковому номеру константы в enum
			return AmountDeterminant.values()[value];
		}
	}

	public AbstractTrader(String name, AmountDeterminant amountDeterminant, float amountValue) {
		/*if (amountDeterminant < AmountDeterminant.FIXED || amountDeterminant > AmountDeterminant.PERCENT_QUOTED) {
			throw new IllegalArgumentException("Некорректный тип определения суммы сделки");
		}*/
		if (amountDeterminant == null)
			throw new IllegalArgumentException(name + ". Amount determinant can't be null");
		this.amountDeterminant = amountDeterminant;
		this.amountValue = amountValue;
		this.name = name;
		this.id = name;
	}

	public AbstractTrader(TraderConfig trader) {
		this(trader.getName(), AmountDeterminant.valueOf(trader.getDealAmountType()), trader.getDealAmountSize());
		this.id = trader.getId();
	}

	public abstract boolean isRealTrader();

	protected Logger getLogger() {
		return LOGGER;
	}

	public void setDealAmountParameters(AmountDeterminant amountDeterminant, float amountValue) {
		if (amountDeterminant == null)
			throw new IllegalArgumentException(name + ". Amount determinant can't be null");
		if (!Objects.equals(this.amountDeterminant, amountDeterminant))
			getLogger().debug(String.format("New amountDeterminant = %s", amountDeterminant));
		this.amountDeterminant = amountDeterminant;
		if (!Objects.equals(this.amountValue, amountValue))
			getLogger().debug(String.format("New amountValue = %s", amountValue));
		this.amountValue = amountValue;
	}

	public void setCurrenciesLimit(float baseCurrency, float quotedCurrency) {
		baseCurrencyAmount = baseCurrency;
		quotedCurrencyAmount = quotedCurrency;
	}

	public void setCurrencyPair(String baseCurrency, String quotedCurrency) {
		this.baseCurrency = baseCurrency;
		this.quotedCurrency = quotedCurrency;
		requestCurrenciesAmount();
	}

	public Integer getLeverage() {
		return 1;
	}

	public abstract void requestCurrenciesAmount();

	public float getFreeFundsDealCoefficient() {
		return freeFundsDealCoefficient;
	}

	public void setFreeFundsDealCoefficient(float value) {
		freeFundsDealCoefficient = value;
	}

	public String canBuy(float desiredRate) {
		requestCurrenciesAmount();
		if (quotedCurrencyAmount < 0.01)
			return "No Money? No Funny Bunny, Honey!";
		double dealAmount = getDealAmount(desiredRate, true);
		if (getFreeFundsDealCoefficient() * quotedCurrencyAmount < dealAmount * desiredRate / getLeverage())
			return "Insufficient funds to open the deal";
		if (quotedCurrencyAmount / quotedCurrencyTotalAmount < freeFundsMarginCoefficient)
			return "Insufficient funds to maintain opened deals";
		return null;
	}

	/*public OrderInfoObject limitBuy(DealInfo info) {
		return openBuy(info).getBaseOrder();
	}*/

	public abstract AbstractPositionContainer openBuy(DealInfo info);

	public abstract boolean preOpenBuy(DealInfo info);

	public String canSell(float desiredRate) {
		requestCurrenciesAmount();
		if (quotedCurrencyAmount < 0.01)
			return "No Money? No Funny Bunny, Honey!";
		double dealAmount = getDealAmount(desiredRate, false);
		if (getFreeFundsDealCoefficient() * quotedCurrencyAmount < dealAmount * desiredRate / getLeverage())
			return "Insufficient funds to open the deal";
		if (quotedCurrencyAmount / quotedCurrencyTotalAmount < freeFundsMarginCoefficient)
			return "Insufficient funds to maintain opened deals";
		return null;
	}

	/*public OrderInfoObject limitSell(DealInfo info) {
		return openSell(info).getBaseOrder();
	}*/

	public abstract AbstractPositionContainer openSell(DealInfo info);

	public abstract boolean preOpenSell(DealInfo info);

	public abstract boolean closeBuy(DealInfo info);

	public abstract boolean closeSell(DealInfo info);

	public abstract boolean changeStopBuy(DealInfo info);

	public abstract boolean changeStopSell(DealInfo info);

	public boolean notify(Map<String, Object> parameters) {
		// можно также использовать для других видов стратегий,
		// чтобы уведомлять об изменении тренда, необходимости разворота сделки и т. д.
		return true;
	}

	private boolean isFartherStopLossAllowed() {
		return allowFartherStopLoss;
	}

	public abstract boolean checkOpenedPositions(AbstractRate<?> lastRate);

	public abstract void saveOrdersState();

  public abstract void configureDefault();

	public void setAuthInfo(TraderAuthenticator authenticator) {
		this.authenticator = authenticator;
	}

	protected TraderAuthenticator getAuthenticator() {
		return authenticator;
	}

	public String setTgChatId(String tgChatId) {
		if (!Objects.equals(this.tgChatId, tgChatId)) {
			this.tgChatId = tgChatId;
			return String.format("Reconfigure trader %s. New tgChatId = %s", getName(), tgChatId);
		}
		return null;
	}

	public String setOpenByMarket(boolean value) {
		if (!Objects.equals(this.openByMarket, value)) {
			this.openByMarket = value;
			return String.format("Reconfigure trader %s. New openByMarket = %s", getName(), openByMarket);
		}
		return null;
	}

	public String updateName(String name) {
		if (!Objects.equals(this.name, name)) {
			String oldName = this.name;
			this.name = name;
			return String.format("Reconfigure trader %s. New name = %s", oldName, name);
		}
		return null;
	}

	public boolean validateTraderChatId(String chatId) {
		return Objects.equals(tgChatId, chatId);
	}

	protected boolean notifyUser(String message) {
		return notifyUser(message, null);
	}

	protected boolean notifyUser(String message, String filename) {
		getLogger().info(AbstractTrader.removeHtmlTags(message));
		return false;
	}

	public static String removeHtmlTags(String html) {
		return html.replaceAll("<.*?>", "");
	}

	public boolean hasOpenedLongPositions() {
		return false;
	}

	public boolean hasOpenedShortPositions() {
		return false;
	}

	public float getCurrentLongProfitability(float curRate) {
		return 0.f;
	}

	public float getCurrentShortProfitability(float curRate) {
		return 0.f;
	}

	public DealInfo getOpenLongPositionInfo() {
		return null;
	}

	public String getOpenLongPositionDetails() {
		DealInfo di = getOpenLongPositionInfo();
		return di == null ? null : di.getBasicDescription();
	}

	public DealInfo getOpenShortPositionInfo() {
		return null;
	}

	public String getOpenShortPositionDetails() {
		DealInfo di = getOpenShortPositionInfo();
		return di == null ? null : di.getBasicDescription();
	}

	protected AppUser findOwnerUser() {
		AppUser user = new AppUser();
		user.setUserName("admin");
		return user;
		/*try {
			return DatabaseInteractor.findOwnerUser(getId());
		} catch (Exception e) {
			LoggingUtils.logError(getLogger(), e, "Can't find owner of trader " + getId());
			return null;
		}*/
	}

	/**
	 * Метод для задания кастомных параметров трейдера
	 * @param name - наименование параметра
	 * @param value - значение параметра
	 * @return сообщение о смене значения параметра, если таковая имела место. Иначе null
	 */
	public String setParameter(String name, String value) {
		switch (name) {
			case "openByMarket" -> {
				return setOpenByMarket(Boolean.parseBoolean(value));
			}
			case "telegramNotificationsChat" -> {
				return setTgChatId(value);
			}
			default -> {
				return "Unknown parameter: " + name;
			}
		}
	}

	public abstract void customRuntimeReconfig();

	protected double getDealAmount(float rate, boolean buy) {
		switch (amountDeterminant) {
			case PERCENT_BASE -> {
				return (baseCurrencyAmount * amountValue) / 100;
			}
			case FIXED_BASE -> {
				return amountValue;
			}
			case FIXED_QUOTED -> {
				return TradeTools.inverse(rate, (double) amountValue * getLeverage());
			}
			case PERCENT_QUOTED -> {
				return TradeTools.inverse(rate, ((double) quotedCurrencyAmount * amountValue * getLeverage()) / 100);
			}
			default -> {
				getLogger().warn("Unknown amount determinant type: " + amountDeterminant);
				return 0;
			}
		}
	}

	public String getBalance() {
		return String.format("Base cur: %s. Quoted cur: %s.", baseCurrencyAmount, quotedCurrencyAmount);
	}

	protected boolean checkTakeProfit(AbstractPositionContainer position, float takeProfit, float delta) {
		if (allowUnprofitableTakeProfit)
			return true;
		/*if (position.getTakeProfitOrder() != null &&
				StatisticCalculator.valuesAlmostEquals(position.getTakeProfitOrder().getPrice(), takeProfit, delta))
			return false;*/
		String side = position.getSide();
		if (Consts.BUY.equals(side)) {
			return takeProfit > position.getPositionInfo().getAveragePrice();
		} else {
			return takeProfit < position.getPositionInfo().getAveragePrice();
		}
	}

	protected boolean hasBetterStopLoss(AbstractPositionContainer position, Float newStopLoss, float delta) {
		if (position.getStopLossOrder() == null || isFartherStopLossAllowed())
			return false;
		if (newStopLoss == null)
			return true;
		//Float positionPrice = position.getPositionInfo().getAveragePrice();
		float curSl = position.getStopLossOrder().getPrice();
		getLogger().debug("Has better stop loss? POS: " + position.getPositionInfo().getAveragePrice() +
				", CUR_SL: " + curSl + ", NEW_SL: " + newStopLoss + ", delta: " + delta);

		if (Consts.BUY.equals(position.getSide()))
			//return curSl > newStopLoss * (1.f + delta);
			return newStopLoss < curSl * (1.f + delta);
		else
			//return curSl < newStopLoss * (1.f - delta);
			return newStopLoss > curSl * (1.f - delta);
		/*float currentSlRange = Math.abs(positionPrice - position.getStopLossOrder().getPriceF());
		float newSlRange = Math.abs(positionPrice - newStopLoss);
		return newSlRange >= currentSlRange;*/
	}

	protected String preparePreDealNotificationMessage(float price, boolean buy) {
		return String.format("%s. Preparing to open %s deal on %s:%s. Price: %.4f",
				this, buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, price);
	}

	protected String prepareDealNotificationMessage(float price, double amount, Float tp, Float sl, boolean buy) {
		return String.format("<b>%s</b>: Opened %s deal on %s:%s. Price: %.4f (TP:%.4f/SL:%.4f), amount: %.4f",
				getName(), buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, price, tp, sl, amount);
	}

	protected String prepareDealFailedNotificationMessage(String message, boolean buy) {
		return String.format("<b>%s</b>: Opening %s deal on %s:%s failed. %n%s",
				getName(), buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, message);
	}

	protected String prepareDealClosedNotificationMessage(String side, Float rate, Float profit, String initiator) {
		return String.format("<b>%s</b>: %s deal on %s:%s closed (by %s) at %s with profit = %.2f",
				getName(), side, baseCurrency, quotedCurrency, initiator, rate, profit);
	}

	protected String prepareStopChangedNotificationMessage(String side, String stopType, Float stopRate,
																												 float expectedProfit, String strategy) {
		return String.format("<b>%s</b>: %s for %s deal on %s:%s changed (%s). New value: %s. Expected profit: %.2f",
				getName(), stopType, side, baseCurrency, quotedCurrency, strategy, stopRate, expectedProfit);
	}

	protected String prepareStopNotChangedNotificationMessage(String side, String stopType, Float stopRate, String strategy) {
		return String.format("<b>%s. Warning</b>: %s for %s deal on %s:%s can not be changed (%s) on %s",
				getName(), stopType, side, baseCurrency, quotedCurrency, strategy, stopRate);
	}

	protected String prepareExpiredOrderCancelledMessage(String direction, String positionId) {
		return String.format("<b>%s</b>: %s order (position: %s) on %s:%s wasn't filled and was cancelled",
				getName(), direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String preparePositionErrorMessage(String direction, String positionId) {
		return String.format("<b>%s</b>: %s position (%s) on %s:%s caused an error.\n" +
						"Position removed from tracking, please, close it on your own!",
				getName(), direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String prepareNotActualStopsMessage(String direction, String positionId) {
		return String.format("<b>%s</b>: Stop-loss & take-profit for %s position %s on  %s:%s are not actual. Removing position",
				getName(), direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String prepareOrderFailedNotificationMessage(String message, boolean buy) {
		return String.format("<b>%s</b>: Posting %s order on %s:%s failed. %n%s",
				getName(), buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, message);
	}

	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return String.format("%s (%s)", getName(), this.getClass().getSimpleName());
	}

	public void addEventListener(ActionListener l) {
		listeners.add(l);
	}

	public void dealOpened() {
		for (ActionListener l : listeners) {
			getLogger().debug("Notifying about opened deal");
			l.actionPerformed(new DealOpenEvent(this));
		}
	}

	public static class DealInfo {
		public static final String PARAM_TREND = "trend";
		public static final String PARAM_TIME = "time";
		public static final String PARAM_FILE = "file";
		public static final String PARAM_STRATEGY = "strategy";
		public static final String PARAM_LAST_RATE = "last_rate";
		public static final String PARAM_MANUAL_LIMIT = "manual_limit";
		public static final String PARAM_POSITION_TIME = "position_open_time";
		public static final String PARAM_FILTERED_DEAL = "filtered_deal";
		public static final String PARAM_FORCE_STOP_CHANGE = "FORCE_STOP_CHANGE";
		private float dealRate;
		private Float stopLoss;
		private Float takeProfit;
		//private boolean market;
		private String baseCurrency = null;
		private String quotedCurrency = null;
		private Map<String, Object> parameters = null;
		public DealInfo(float dealRate, String baseCur, String quotedCur) {
			this(dealRate, (Float) null, null);
			setCurrencyPair(baseCur, quotedCur);
		}

		/*public DealInfo(float dealRate, Float stopLoss, Float takeProfit) {
			this(dealRate, stopLoss, takeProfit, false);
		}*/

		public DealInfo(float dealRate, Float stopLoss, Float takeProfit, final Map<String, Object> params) {
			this.dealRate = dealRate;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			//this.market = market;
			this.parameters = params;
		}

		public DealInfo(float dealRate, Float stopLoss, Float takeProfit) {
			this.dealRate = dealRate;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			//this.market = market;
		}

		public float getDealRate() {
			return dealRate;
		}

		public Float getStopLoss() {
			return stopLoss;
		}

		public Float getTakeProfit() {
			return takeProfit;
		}

		/*public boolean isMarket() {
			return market;
		}*/

		public String getBaseCurrency() {
			return baseCurrency;
		}

		public String getQuotedCurrency() {
			return quotedCurrency;
		}

		public void setCurrencyPair(String baseCurrency, String quotedCurrency) {
			this.baseCurrency = baseCurrency;
			this.quotedCurrency = quotedCurrency;
		}

		public DealInfo addParameter(String name, Object value) {
			if (parameters == null)
				parameters = new HashMap<>();
			parameters.put(name, value);
			return this;
		}

		public boolean hasParameter(String name) {
			return parameters != null && parameters.containsKey(name);
		}

		//TODO доработать функционал, чтобы можно было вычислять значения только при необходимости (если к ним обращаются)
		public Object getParameter(String name) {
			if (hasParameter(name))
				return parameters.get(name);
			return null;
		}

		@Override
		public String toString() {
			return super.toString() + " " + getBasicDescription();
		}

		public String getBasicDescription() {
			return "DR: " + getDealRate() + " SL: " + getStopLoss() + " TP: " + getTakeProfit();
		}
	}

	public static class DealOpenEvent extends ActionEvent {
		public DealOpenEvent(Object source) {
			super(source, ACTION_PERFORMED, "deal_opened");
		}
	}
}
