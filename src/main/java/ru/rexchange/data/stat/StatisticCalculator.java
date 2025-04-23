package ru.rexchange.data.stat;

import org.apache.commons.lang3.ArrayUtils;
import ru.rexchange.data.Consts;
import ru.rexchange.data.storage.AbstractRate;
import ru.rexchange.data.storage.RateCandle;
import ru.rexchange.data.storage.RateSequence;
import ru.rexchange.exception.SystemException;

import java.util.*;

//TODO find good external library
public class StatisticCalculator {
	public static float getAverage(Collection<Float> data) {
		if (data.isEmpty())
			return 0.f;
		double result = 0;
		for (float value : data) {
			result += value;
		}
		return (float) (result / data.size());
	}

	public static float getAverage(List<Float> data, int startIndex) {
		float result = 0;
		for (int i = startIndex; i < data.size(); i++) {
			result += data.get(i);
		}
		return result / (data.size() - startIndex);
	}

	public static float getAverageCustom(Iterable<RateCandle> candles, RateCandle.PriceGetter<Float> getter) {
		float result = 0;
		int counter = 0;
		for (RateCandle r : candles) {
			result += getter.getPrice(r);
			counter++;
		}
		return result / counter;
	}

	public static Float getAverageAdaptive(List<Float> data, int index, int period) {
		float result = 0;
		/*int len = (index - period >= 0 ? period : index + 1);
		//float[] accumulator = new float[(index - period > 0 ? period : index + 1)];
		for (int i = index - len + 1; i <= index; i++) {
			result += data.get(i);
		}*/
		int count = 0;
		for (int i = index; i >= 0 && count < period; i--, count++) {
			result += data.get(i);
		}
		return result / count;
	}

	public static float getLastAverage(List<Float> data, int length) {
		if (data.size() < length)
			return 0f;
		float result = 0;
		for (int i = data.size() - length; i < data.size(); i++) {
			result += data.get(i);
		}
		return result / length;
	}

	public static float getLastExpAverage(List<Float> data, int period) {
		if (data.isEmpty())
			return 0.f;
		float smoothingConstant = 2f / (period + 1);
		Float lastValue = null;
		for (Float rate : data) {
			float nextValue;
			if (lastValue == null) {
				nextValue = rate;
			} else {
				// Formula: (Close - EMA(previous day)) x multiplier + EMA(previous day)
				nextValue = (rate - lastValue) * smoothingConstant + lastValue;
				// Formula: (Close * multiplier) + (1 - multiplier) * EMA(previous day) (same results)
				//nextValue = (values.get(i + period - 1) * smoothingConstant) + (1 - smoothingConstant) * last;
			}
			lastValue = nextValue;
		}
		return lastValue;
	}

	public static float getLastAverageAdaptive(List<Float> data, int length) {
		int dataSize = data.size();
		if (dataSize == 0)
			return 0f;
		float result = 0;
		for (int i = Math.max(0, dataSize - length); i < dataSize; i++) {
			result += data.get(i);
		}
		return result / Math.min(length, dataSize);
	}

	public static float getLastAverageAdaptive(RateSequence rates, int length) {
		int dataSize = rates.size();
		if (dataSize == 0)
			return 0f;
		float result = 0;
		List<RateCandle> subList = rates.subList(Math.max(0, dataSize - length), dataSize);
		for (RateCandle rate : subList) {
			result += rate.getClose();
		}
		/*for (int i = Math.max(0, dataSize - length); i < dataSize; i++) {
			result += rates.get(i).getClose();
		}*/
		return result / Math.min(length, dataSize);
	}

	public static float getAverage(List<Float> data, int startIndex, int length) {
		if (data.size() < length + startIndex)
			return 0f;
		float result = 0;
		for (int i = startIndex; i < startIndex + length; i++) {
			result += data.get(i);
		}
		return result / length;
	}

	public static float getFirstAverageAdaptive(List<Float> data, int length) {
		int dataSize = data.size();
		if (dataSize == 0)
			return 0f;
		float result = 0;
		for (int i = 0; i < Math.min(length, dataSize); i++) {
			result += data.get(i);
		}
		return result / Math.min(length, dataSize);
	}

	public static long getAverageL(Collection<Long> data) {
		long result = 0;
		for (long value : data) {
			result += value;
		}
		return result / data.size();
	}

	public static float getDeviation(Collection<Float> data) {
		return getDeviation(data, getAverage(data));
	}

	public static float getDeviation(Collection<Float> data, float avg) {
		float result = 0;
		for (float value : data) {
			result += Math.pow((value - avg), 2);
		}
		return (float) Math.sqrt(result / data.size());
	}

	public static float getDeviation(List<Float> data, int startIndex) {
		float avg = getAverage(data, startIndex);
		float result = 0;
		for (int i = startIndex; i < data.size(); i++) {
			result += Math.pow((data.get(i) - avg), 2);
		}
		return (float) Math.sqrt(result / (data.size() - startIndex));
	}

	public static float getLastDeviation(List<Float> data, int length) {
		if (data.size() < length)
			return 0f;
		float avg = getLastAverage(data, length);
		return getLastDeviation(data, avg, length);
	}

	public static float getLastDeviation(List<Float> data, float average, int length) {
		if (data.size() < length)
			return 0f;
		float result = 0;
		for (int i = data.size() - length; i < data.size(); i++) {
			result += Math.pow((data.get(i) - average), 2);
		}
		return (float) Math.sqrt(result / length);
	}

	public static float getLastDeviationAdaptive(List<Float> data, float average, int length) {
		int dataSize = data.size();
		if (dataSize == 0)
			return 0f;
		float result = 0;
		for (int i = Math.max(0, dataSize - length); i < dataSize; i++) {
			result += Math.pow((data.get(i) - average), 2);
		}
		return (float) Math.sqrt(result / Math.min(length, dataSize));
	}

	public static float getDeviation(List<Float> data, int startIndex, int length) {
		if (data.size() < length + startIndex)
			return 0f;
		float avg = getAverage(data, startIndex, length);
		float result = 0;
		for (int i = startIndex; i < startIndex + length; i++) {
			result += Math.pow((data.get(i) - avg), 2);
		}
		return (float) Math.sqrt(result / length);
	}

	public static float getDeviation(List<Float> data, float avg, int startIndex, int length) {
		if (data.size() < length + startIndex)
			return 0f;
		float result = 0;
		for (int i = startIndex; i < startIndex + length; i++) {
			result += Math.pow((data.get(i) - avg), 2);
		}
		return (float) Math.sqrt(result / length);
	}

	public static Float getWeightedAverage(List<Float> values, int index, float[] weights) {
		int window = weights.length, halfWindow = window / 2;
		float result = 0.f, weightsSum = 0.f;
		for (int i = 0; i < window; i++) {
			int valuesInd = index - halfWindow + i;
			if (valuesInd < 0 || valuesInd >= values.size())
				continue;
			result += values.get(valuesInd) * weights[i];
			weightsSum += weights[i];
		}
		return result / weightsSum;
	}

	public static Float getWeightedAverage(List<Float> values, float[] weights) {
		float result = 0.f, weightsSum = 0.f;
		int i = 0;
		for (Float value : values) {
			result += value * weights[i];
			weightsSum += weights[i];
			i++;
		}
		return result / weightsSum;
	}

	public static int countTrendChangesForTheLastNValues(List<Float> values, Integer n) {
		return countTrendChangesForTheLastNValues(values, n, 0.f);
	}

	public static int countTrendChangesForTheLastNValues(List<Float> values, Integer n, float delta) {
		int changeCounter = 0;
		int dir = Consts.Direction.NEUTRAL;
		Float lastValue = null;
		List<Float> sequenceToCheck = (n == null || values.size() == n) ?
				values :
				values.subList(values.size() - n, values.size());
		for (float curValue : sequenceToCheck) {
			if (lastValue != null) {
				if (curValue > lastValue + delta) {
					if (dir == Consts.Direction.DOWN)
						changeCounter++;
					dir = Consts.Direction.UP;
				} else if (curValue < lastValue - delta) {
					if (dir == Consts.Direction.UP)
						changeCounter++;
					dir = Consts.Direction.DOWN;
				}
			}
			lastValue = curValue;
		}
		return changeCounter;
	}

	public static float getMinimumLow(Iterable<? extends AbstractRate<Float>> candles) {
		float result = Float.MAX_VALUE;
		for (AbstractRate<Float> ar : candles) {
			float low = ar instanceof RateCandle ? ((RateCandle) ar).getLow() : ar.getValue();
			if (low < result)
				result = low;
		}
		return result;
	}

	@Deprecated(/*replace usage with getMinimumCustom*/)
	public static float getMinimumSmoothLow(Iterable<RateCandle> candles) {
		float result = Float.MAX_VALUE;
		for (RateCandle r : candles) {
			if (r.getSmoothLow() < result)
				result = r.getSmoothLow();
		}
		return result;
	}

	public static float getMinimumCustom(Iterable<RateCandle> candles, RateCandle.PriceGetter<Float> getter) {
		float result = Float.MAX_VALUE;
		for (RateCandle r : candles) {
			if (getter.getPrice(r) < result)
				result = getter.getPrice(r);
		}
		return result;
	}

	public static float getMaximumHigh(Iterable<? extends AbstractRate<Float>> candles) {
		float result = Float.MIN_VALUE;
		for (AbstractRate<Float> ar : candles) {
			float high = ar instanceof RateCandle ? ((RateCandle) ar).getHigh() : ar.getValue();
			if (high > result)
				result = high;
		}
		return result;
	}

	@Deprecated(/*replace usage with getMaximumCustom*/)
	public static float getMaximumSmoothHigh(Iterable<RateCandle> candles) {
		float result = Float.MIN_VALUE;
		for (RateCandle r : candles) {
			if (r.getSmoothHigh() > result)
				result = r.getSmoothHigh();
		}
		return result;
	}

	public static float getMaximumCustom(Iterable<RateCandle> candles, RateCandle.PriceGetter<Float> getter) {
		float result = Float.MIN_VALUE;
		for (RateCandle r : candles) {
			if (getter.getPrice(r) > result)
				result = getter.getPrice(r);
		}
		return result;
	}

	public static float findMedian(List<Float> list) {
		List<Float> values = new ArrayList<>(list);
		values.sort(Float::compareTo);
		int n = values.size();
		if (n % 2 == 0) {
			return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.f;
		} else {
			return values.get(n / 2);
		}
	}

	public static List<Float> evaluateDifferenceFactors(List<Float> mainSequence, List<Float> dependSequence) {
		if (mainSequence.size() != dependSequence.size())
			throw new SystemException("Input sequences must have the same sizes");
		List<Float> result = new LinkedList<>();
		for (int i = 0; i < mainSequence.size(); i++) {
			Float depValue = dependSequence.get(i);
			result.add((mainSequence.get(i) - depValue) / depValue);
		}
		return result;
	}

	public static List<Float> divide(List<Float> mainSequence, List<Float> divisorSequence) {
		if (mainSequence.size() != divisorSequence.size())
			throw new SystemException("Input sequences must have the same sizes");
		List<Float> result = new LinkedList<>();
		for (int i = 0; i < mainSequence.size(); i++) {
			Float depValue = divisorSequence.get(i);
			result.add(mainSequence.get(i) / depValue);
		}
		return result;
	}

	public static float getMinimum(List<Float> values) {
		return values.stream().min(Float::compareTo).orElse(Float.MIN_VALUE);
	}

	public static float getMaximum(List<Float> values) {
		return values.stream().max(Float::compareTo).orElse(Float.MIN_VALUE);
	}

	public static List<Integer> getFirstFibonacci(int n) {
		if (n <= 0) {
			throw new IllegalArgumentException("N must be positive integer");
		}

		List<Integer> fib = new ArrayList<>(n);
		fib.add(0);
		if (n > 1) {
			fib.add(1);
		}

		for (int i = 2; i < n; i++) {
			fib.add(fib.get(i - 1) + fib.get(i - 2));
		}
		return fib;
	}

	public static float putInRange(float value, float min, float max) {
		return Math.min(Math.max(value, min), max);
	}

  public static boolean valuesAlmostEquals(float valueA, float valueB, float deltaFactor) {
    return Math.abs(valueA - valueB) / Math.abs(valueA) < deltaFactor;
  }

  public static float[] smoothOutliers(float[] data) {
    if (data == null || data.length == 0) {
      return data;
    }

    // Сортируем массив для нахождения квартилей
    float[] sortedData = Arrays.copyOf(data, data.length);
    Arrays.sort(sortedData);

    // Находим первый и третий квартили
    float q1 = percentile(sortedData, 25);
    float q3 = percentile(sortedData, 75);

    // Находим интерквартильный размах (IQR)
    float iqr = q3 - q1;
		if (iqr == 0.f) {//todo может просто брать среднее всегда между iqr и dev?
			iqr = getDeviation(Arrays.asList(ArrayUtils.toObject(data))) * 0.5f;
		}

    // Определяем границы для выбросов
    float lowerBound = q1 - 1.5f * iqr;
    float upperBound = q3 + 1.5f * iqr;

    // Сглаживаем выбросы
    for (int i = 0; i < data.length; i++) {
      if (data[i] < lowerBound) {
        data[i] = lowerBound;
      } else if (data[i] > upperBound) {
        data[i] = upperBound;
      }
    }

    return data;
  }

	public static boolean hasOutliers(Float[] data, float strengthThreshold) {
		if (data == null || data.length == 0) {
			return false;
		}

		// Сортируем массив для нахождения квартилей
		Float[] sortedData = Arrays.<Float>copyOf(data, data.length);
		Arrays.sort(sortedData);

		float q1 = percentile(sortedData, 25);
		float q3 = percentile(sortedData, 75);
		float iqr = q3 - q1;

		float lowerBound = q1 - strengthThreshold * iqr;
		float upperBound = q3 + strengthThreshold * iqr;

		for (float value : sortedData) {
		    if (value > upperBound || value < lowerBound)
					return true;
		}
		return false;
	}

	public static boolean lastIsOutlier(Float[] data, float rangeFactor) {
		if (data == null || data.length == 0) {
			return false;
		}

		// Сортируем массив для нахождения квартилей
		Float[] sortedData = Arrays.<Float>copyOf(data, data.length);
		Arrays.sort(sortedData);

		float q1 = percentile(sortedData, 25);
		float q3 = percentile(sortedData, 75);
		float iqr = q3 - q1;

		float lowerBound = q1 - rangeFactor * iqr;
		float upperBound = q3 + rangeFactor * iqr;

		Float lastValue = data[data.length - 1];
		return lastValue > upperBound || lastValue < lowerBound;
	}

  public static float percentile(float[] data, float percentile) {
    int index = (int) Math.ceil((percentile / 100.0) * data.length) - 1;
    return data[index];
  }

  public static float percentile(Float[] data, float percentile) {
    int index = (int) Math.ceil((percentile / 100.0) * data.length) - 1;
    return data[index];
  }

	public static void main(String[] args) {
		float[] data = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 19.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f};

		System.out.println("Original data: " + Arrays.toString(data));
		System.out.println("Has outliers: " + hasOutliers(ArrayUtils.toObject(data), 1.5f));
		float[] smoothedData = StatisticCalculator.smoothOutliers(data);
		System.out.println("Smoothed data: " + Arrays.toString(smoothedData));

		System.out.println("Has outliers: " + hasOutliers(ArrayUtils.toObject(data), 1.5f));

	}

	public static double normalizeRatio(double ratio, double k) {
		return 1. / (1. + Math.exp(-k * Math.log10(ratio)));
	}

	/**
	 * Нормирует отношения двух положительных величин, имеющих примерно одинаковое распределение
	 * @param first - первая величина
	 * @param second - вторая величина
	 * @param k - коэффициент величины изгиба
	 * @return значение от ~0 (если first << second) до ~1 (если first >> second)
	 */
	public static double normalizeRatio(float first, float second, double k) {
		return 1. / (1. + Math.exp(-k * Math.log10(first / second)));
	}

	/**
	 * Мягко нормирует величину диапазона [0; 6] в диапазон [0; 1]; 0.5 ~> 0.5, 1.0 ~> 0.7
	 */
	public static float mildSigmoidNorm(float value) {
		return (1.1f * value) / (0.6f + value);
	}

  public static float[] calculateGaussWeights(int size, float offset, float sigma) {
    float[] w = new float[size];
    double m = offset * (size - 1);
    double s = size / sigma;

    double sum = 0;
    for (int i = 0; i < size; i++) {
      w[i] = (float) Math.exp(-((i - m) * (i - m)) / (2 * s * s));
      sum += w[i];
    }

    // Нормализация весов
    for (int i = 0; i < size; i++) {
      w[i] /= (float) sum;
    }

    return w;
  }

	/**
	 *
	 * @param value
	 * @param k
	 * @param a
	 * @return
	 */
	public static float sigmoidNorm(float value, float k, float a) {
		return k * (a * value) / (a - value + 1);
	}
}
