package ru.rexchange.trading.trader;

import binance.futures.enums.OrderSide;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import ru.rexchange.data.Consts;
import ru.rexchange.data.stat.StatisticCalculator;
import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.db.DatabaseInteractor;
import ru.rexchange.gen.AppUser;
import ru.rexchange.gen.TraderConfig;
import ru.rexchange.tools.DateUtils;
import ru.rexchange.tools.LoggingUtils;
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
	@Getter @Setter
	private boolean active = true;

	//TODO трейдеру (фьючей) необязательно хранить каждую валюту
	// для ордеров можно передавать в DealInfo
	// при загрузке состояния можно передавать в параметрах при вызове из бота
	// при сохранении состояния использовать данные из самого ордера (можно хранить в виде символа пары)
	// однако, нужно как-то выяснять доступный баланс
	protected String baseCurrency;
	protected String quotedCurrency;
	protected float baseCurrencyAmount = 0f;
	protected float quotedCurrencyAmount = 0f;
	protected float baseCurrencyTotalAmount = 0f;
	protected float quotedCurrencyTotalAmount = 0f;
	protected AmountDeterminant amountDeterminant;
	protected double amountValue = 10.0;
	@Setter
  @Getter
  protected float freeFundsDealCoefficient = 0.5f;
	protected float freeFundsMarginCoefficient = 0.25f;//todo определять в зависимости от риска
	@Getter
	protected String tgChatId = null;
	protected boolean allowFartherStopLoss = false;
	protected boolean allowUnprofitableTakeProfit = false;
	private TraderAuthenticator authenticator;
	protected boolean openByMarket = false;
	protected final Set<ActionListener> listeners = new HashSet<>();
	private  Float maximumLossAllowed = null;//0.5f;//todo configurable

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
		this(trader.getName(), AbstractTrader.AmountDeterminant.valueOf(trader.getDealAmountType()), trader.getDealAmountSize());
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

	public String setQuotedCurrency(String symbol) {
		if (!Objects.equals(this.quotedCurrency, symbol)) {
			this.quotedCurrency = symbol;
			requestCurrenciesAmount();
			return String.format("Reconfigure trader %s. New quotedCurrency = %s", getName(), symbol);
		}
		return null;
	}

	public Integer getLeverage() {
		return 1;
	}

	public abstract void requestCurrenciesAmount();

	public abstract float getSummaryProfit(float rate);

  public String canBuy(float desiredRate) {
		if (!isActive())
			return "Trader is disabled";
		requestCurrenciesAmount();
		//todo о некоторых причинах следовало бы уведомлять пользователя, как их разделять?
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
		if (!isActive())
			return "Trader is disabled";
		requestCurrenciesAmount();
		//todo о некоторых причинах следовало бы уведомлять пользователя, как их разделять?
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

	public abstract boolean checkOpenedPositions(AbstractRate<? extends Number> lastRate);

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
		getLogger().info(message);
		return false;
	}

	@NotNull
	private String formatMessage(String message) {
		return String.format("<b>%s</b>:\n" + message, getName()).
				replace("<<", "<b>").replace(">>", "</b>");
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
		try {
			return DatabaseInteractor.findOwnerUser(getId());
		} catch (Exception e) {
			LoggingUtils.logError(getLogger(), e, "Can't find owner of trader " + getId());
			return null;
		}
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
			case "quotedCurrency" -> {
				return setQuotedCurrency(value);
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
        getLogger().warn("Unknown amount determinant type: {}", amountDeterminant);
				return 0;
			}
		}
	}

	public String getBalance() {
		return String.format("Base cur: %s. Quoted cur: %s.", baseCurrencyAmount, quotedCurrencyAmount);
	}

	protected String checkStopLoss(DealInfo info, AbstractPositionContainer position, float delta) {
		boolean forceSLChange = info.hasParameter(DealInfo.PARAM_FORCE_STOP_CHANGE) &&
				(boolean) info.getParameter(DealInfo.PARAM_FORCE_STOP_CHANGE);
		if (forceSLChange)
			return null;
		Float stopLoss = info.getStopLoss();
		if (position.getStopLossOrder() != null) {
			if (StatisticCalculator.valuesAlmostEquals(position.getStopLossOrder().getPrice(), stopLoss, delta))
				return "New stop-loss is too close to the existing one";
			if (hasBetterStopLoss(position, stopLoss, delta))
				return "New stop-loss is worse than the existing one";
		}
		Float maximumLoss = getMaxLossAllowed();
		if (maximumLoss != null) {
			float profit = position.getExpectedProfit(stopLoss);
			if (profit < 0f && profit < -maximumLoss)
				return "Expected loss is too large: " + profit;
		}
		return null;
	}

	private Float getMaxLossAllowed() {
		if (maximumLossAllowed == null)
			return null;
		if (maximumLossAllowed > 1f)
			return maximumLossAllowed;
		if (maximumLossAllowed > 0f)
			return quotedCurrencyAmount * maximumLossAllowed;//todo depends on amountDeterminant?
		return null;
	}

	protected String checkTakeProfit(DealInfo info, AbstractPositionContainer position, float delta) {
		if (allowUnprofitableTakeProfit)
			return null;
		boolean forceTPChange = info.hasParameter(DealInfo.PARAM_FORCE_STOP_CHANGE) &&
				(boolean) info.getParameter(DealInfo.PARAM_FORCE_STOP_CHANGE);
		if (forceTPChange)
			return null;
		Float takeProfit = info.getTakeProfit();
		if (position.getTakeProfitOrder() != null) {
			if (StatisticCalculator.valuesAlmostEquals(position.getTakeProfitOrder().getPrice(), takeProfit, delta))
				return "New take-profit is too close to the existing one";
		}
		String side = position.getSide();
		if (Consts.BUY.equals(side)) {
			return takeProfit > position.getPositionInfo().getAveragePrice() ? null : "New take-profit is lossy";
		} else {
			return takeProfit < position.getPositionInfo().getAveragePrice() ? null : "New take-profit is lossy";
		}
	}

	protected boolean hasBetterStopLoss(AbstractPositionContainer position, Float newStopLoss, float delta) {
		//todo возвращать описание причины
		if (newStopLoss == null)
			return true;
		if (position.getStopLossOrder() == null)
			return false;
		int direction = Consts.BUY.equals(position.getSide()) ? Consts.Direction.UP : Consts.Direction.DOWN;
		float curSl = position.getStopLossOrder().getPrice();
		float posAvgValue = position.getAvgPrice().floatValue();

		// проверяем на убыточность нового стоп-лосса по сравнению с текущим, безубыточный на убыточный не меняем
		boolean newStopLossIsLossy = direction * (newStopLoss - posAvgValue) < 0;
		boolean curStopLossIsProfitable = direction * (curSl - posAvgValue) > 0;
		if (curStopLossIsProfitable && newStopLossIsLossy) {
			return true;
		}

		// по настройке можем отодвигать только убыточные стоп-лоссы
		if (isFartherStopLossAllowed() && newStopLossIsLossy)
			return false;
    getLogger().debug("Has better stop loss? POS: {}, CUR_SL: {}, NEW_SL: {}, delta: {}",
				posAvgValue, curSl, newStopLoss, delta);

		/*boolean oldResult;
		if (Consts.BUY.equals(position.getSide()))
			//return curSl > newStopLoss * (1.f + delta);
			oldResult = newStopLoss < curSl * (1.f + delta);
		else
			//return curSl < newStopLoss * (1.f - delta);
			oldResult = newStopLoss > curSl * (1.f - delta);*/

		//проверка на то, что новый стоп-лосс как минимум на delta лучше текущего
		boolean decline = newStopLoss * direction < curSl * direction * (1 + delta * direction);
		/*if (oldResult != decline)
			LOGGER.warn("It cant be {} != {}", oldResult, decline);*/
		return decline;
	}

	protected String preparePreDealNotificationMessage(float price, boolean buy) {
		return String.format("Preparing to open %s deal on %s:%s. Price: %.4f",
				buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, price);
	}

	protected String prepareDealNotificationMessage(float price, double amount, Float tp, Float sl, boolean buy) {
		return String.format("Opened %s deal on %s:%s.\nPrice: %.4f (TP:%.4f/SL:%.4f), amount: %.4f",
				buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, price, tp, sl, amount);
	}

	protected String prepareDealFailedNotificationMessage(String message, boolean buy) {
		return String.format("Opening %s deal on %s:%s failed. %n%s",
				buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, message);
	}

	protected String prepareDealClosedNotificationMessage(String side, Float rate, long closeTime, Float profit, String initiator) {
		return String.format("%s deal on %s:%s closed (by %s) at %s (%s).\n<<Profit = %.2f>>",
				side, baseCurrency, quotedCurrency, initiator, rate, DateUtils.formatTimeMin(closeTime), profit);
	}

	protected String preparePositionPartiallyClosedNotificationMessage(String side, Float rate, long closeTime, String initiator) {
		return String.format("%s deal on %s:%s partially closed (by %s) at %s (%s) ",
				side, baseCurrency, quotedCurrency, initiator, rate, DateUtils.formatTimeMin(closeTime));
	}

	protected String prepareStopChangedNotificationMessage(String side, String stopType, Float stopRate,
																												 float expectedProfit, String strategy) {
		return String.format("%s for %s deal on %s:%s changed (%s). New value: %s.\n<<Expected profit: %.2f>>",
				stopType, side, baseCurrency, quotedCurrency, strategy, stopRate, expectedProfit);
	}

	protected String prepareStopRemovedNotificationMessage(String side, String stopType, String strategy) {
		return String.format("%s for %s deal on %s:%s removed (%s)",
				stopType, side, baseCurrency, quotedCurrency, strategy);
	}

	protected String prepareStopNotChangedNotificationMessage(String side, String stopType, Float stopRate, String strategy) {
		return String.format("<<Warning>>:\n%s for %s deal on %s:%s can not be changed (%s) on %s",
				stopType, side, baseCurrency, quotedCurrency, strategy, stopRate);
	}

	protected String prepareExpiredOrderCancelledMessage(String direction, String positionId) {
		return String.format("%s order (position: %s) on %s:%s wasn't filled and was cancelled",
				direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String preparePositionErrorMessage(String direction, String positionId) {
		return String.format("%s position (%s) on %s:%s caused an error.\n" +
						"Position removed from tracking, please, close it on your own!",
				direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String prepareNotActualStopsMessage(String direction, String positionId) {
		return String.format("Stop-loss & take-profit for %s position %s on  %s:%s are not actual. Removing position",
				direction, positionId, baseCurrency, quotedCurrency);
	}

	protected String prepareOrderFailedNotificationMessage(String message, boolean buy) {
		return String.format("Posting %s order on %s:%s failed. %n%s",
				buy ? "BUY" : "SELL", baseCurrency, quotedCurrency, message);
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
		public static final String PARAM_LAST_ORDER_TIME = "last_order_open_time";
		public static final String PARAM_ORDERS_COUNT = "orders_count";
		public static final String PARAM_POSITION_WEIGHT = "position_weight";
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
