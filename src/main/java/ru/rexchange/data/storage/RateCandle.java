package ru.rexchange.data.storage;

import ru.rexchange.tools.DateUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class RateCandle extends AbstractRate<Float> {
	private final long openTime;
	private long closeTime;
	private float low;
	private float high;
	private float open;
	private float close;
	private double volume;
	private double quoteVolume;

	/*public RateCandle(long openTime, long closeTime, double low, double high, double open, double close) {
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.low = (float) low;
		this.high = (float) high;
		this.open = (float) open;
		this.close = (float) close;
	}*/

	public RateCandle(long openTime, long closeTime, double low, double high, double open, double close,
										double volume, double qVolume) {
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.low = (float) low;
		this.high = (float) high;
		this.open = (float) open;
		this.close = (float) close;
		this.volume = volume;
		this.quoteVolume = qVolume;
	}

	public RateCandle(long openTime, long closeTime, float low, float high, float open, float close, double volume) {
		this.openTime = openTime;
		this.closeTime = closeTime;
		this.low = low;
		this.high = high;
		this.open = open;
		this.close = close;
		this.volume = volume;
	}

	public RateCandle(long time, long period, float rate) {
		super();
		this.openTime = time - period;
		this.closeTime = time;
		this.low = rate;
		this.high = rate;
		this.open = rate;
		this.close = rate;
	}

	public RateCandle(long closeTime, long period, float open, float close) {
		super();
		this.openTime = closeTime - period;
		this.closeTime = closeTime;
		this.low = Math.min(open, close);
		this.high = Math.max(open, close);
		this.open = open;
		this.close = close;
	}

	//TODO хранить open & close в БД
	public static RateCandle fromDbRecord(ru.rexchange.gen.RateValue r, long period) {
		RateCandle result = new RateCandle(r.getRateTimestamp(), period, r.getRateValue());
		//result.setCloseTime(TimeUtils.getPrevPeriodStart(r.getRateTimestamp()) + period);
		return result;
	}

	public static List<Float> getPricesList(Collection<RateCandle> candles, PriceGetter<Float> getter) {
		return candles.stream().map(getter::getPrice).collect(Collectors.toList());
	}

	public long getOpenTime() {
		return openTime;
	}

	public Date getOpenTimeAsDate() {
		return new Date(openTime);
	}

	public long getCloseTime() {
		return closeTime;
	}

	public Date getCloseTimeAsDate() {
		return new Date(closeTime);
	}

	public void setCloseTime(long value) {
		closeTime = value;
	}

	public float getLow() {
		return low;
	}

	public float getHigh() {
		return high;
	}

	public float getSmoothLow() {
		return (2 * low + Math.min(open, close)) / 3;
	}

	public float getLowClose() {
		return (low + close) / 2f;
	}

	public float getHighClose() {
		return (high + close) / 2f;
	}

	public float getSmoothLowClose() {
		return (low + close * 2f) / 3f;
	}

	public float getSmoothHighClose() {
		return (high + close * 2f) / 3f;
	}

	public float getSmoothHigh() {
		return (2 * high + Math.max(open, close)) / 3;
	}

	public float getOpen() {
		return open;
	}

	public float getClose() {
		return close;
	}
	public Float getTypical() {
		return (high + low + close) / 3;
	}

	@Override
	public Float getRelativeChange() {
		return (close - open) / open;
	}

	public float getMedian() {
		return (high + low) / 2;
	}

	public float getAverage() {
		return (open + close) / 2;
	}

	public void setOpen(float open) {
		this.open = open;
	}

	public void setClose(float close) {
		this.close = close;
	}

	public void setLow(float low) {
		this.low = low;
	}

	public void setHigh(float high) {
		this.high = high;
	}

	public double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public double getQuoteVolume() {
		return quoteVolume;
	}

	public void setQuoteVolume(double quoteVolume) {
		this.quoteVolume = quoteVolume;
	}

	@Override
	public String toString() {
		try {
			return DateUtils.formatTimeMin(new Date(openTime)) + ": " + open + " - " + close;
		} catch (Exception e) {
			e.printStackTrace();
			return super.toString();
		}
	}

	public String toString(boolean full) {
		if (!full)
			return toString();
		try {
			return DateUtils.formatTimeMin(new Date(openTime)) + ": " + low  + " - " + open + " - " + close + " - " + high +
					", " + volume;
		} catch (Exception e) {
			e.printStackTrace();
			return super.toString();
		}
	}

	public float getHL2() {
		return (high + low) / 2.f;
	}

	public void fillQuoteVolume() {
		setQuoteVolume(this.volume * getTypical());
	}

	public float getBody() {
		return close - open;
	}

	public void combCandleDumb(float avg, float dev) {
		setLow(fitInRange(low, avg, dev));
		setHigh(fitInRange(high, avg, dev));
		setOpen(fitInRange(open, avg, dev));
		setClose(fitInRange(close, avg, dev));
	}

	public float[] combCandle() {
		float[] rates = new float[] {getOpen(), getClose(), getLow(), getHigh()};
		/*rates = StatisticCalculator.smoothOutliers(rates);
		setOpen(rates[0]);
		setClose(rates[1]);
		setLow(rates[2]);
		setHigh(rates[3]);*/
		return rates;
	}

	private float fitInRange(float value, float avg, float dev) {
		if (value > avg + dev)
			return avg + dev;
		return Math.max(value, avg - dev);
	}

	@Override
	public Float getValue() {
		return getClose();
	}

	public interface PriceGetter<T extends Number> {
		T getPrice(AbstractRate<T> rate);
	}
}
