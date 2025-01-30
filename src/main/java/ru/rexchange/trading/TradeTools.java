package ru.rexchange.trading;

import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.data.storage.RateCandle;
import ru.rexchange.data.storage.RateSequence;

import java.util.List;

public class TradeTools {
	public static double convert(float rate, double amount) {
		return amount * rate;
	}

	public static double inverse(float rate, double amount) {
		return amount / rate;
	}

	public static float evaluateHistoricalSellStopLoss(RateSequence<? extends AbstractRate<Float>, Float> rates, int lastCandlesCount) {
		float max = rates.getLast().getValue();
		List<? extends AbstractRate<Float>> last = rates.getNLastValues(lastCandlesCount);
		for (AbstractRate<Float> rate : last) {
			float high = rate instanceof RateCandle ? ((RateCandle) rate).getHigh() : rate.getValue();
			if (max < high)
				max = high;
		}
		return max;
	}

	public static float evaluateHistoricalBuyStopLoss(RateSequence<? extends AbstractRate<Float>, Float> rates, int lastCandlesCount) {
		float min = rates.getLast().getValue();
		List<? extends AbstractRate<Float>> last = rates.getNLastValues(lastCandlesCount);
		for (AbstractRate<Float> rate : last) {
			float low = rate instanceof RateCandle ? ((RateCandle) rate).getLow() : rate.getValue();
			if (min > low)
				min = low;
		}
		return min;
	}
}
