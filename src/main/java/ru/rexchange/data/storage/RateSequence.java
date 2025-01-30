package ru.rexchange.data.storage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class RateSequence<R extends AbstractRate<T>, T extends Number> extends LinkedList<R> {
	private static final long serialVersionUID = 1L;

	public RateSequence() {
		super();
	}

	public RateSequence(List<R> list) {
		super(list);
	}

	public List<T> getRates() {
		List<T> result = new ArrayList<>();
		for (R value : this) {
			result.add(value.getValue());
		}
		return result;
	}

	public List<T> getRates(RateCandle.PriceGetter<T> getter) {
		List<T> result = new ArrayList<>();
		for (R value : this) {
			result.add(getter.getPrice(value));
		}
		return result;
	}

	public List<T> getRates(int count) {
		LinkedList<T> result = new LinkedList<>();
		int i = 0;
		for (Iterator<R> iterator = this.descendingIterator(); iterator.hasNext() && i < count; i++) {
			R value = iterator.next();
			result.add(0, value.getValue());
		}
		return result;
	}

	public List<T> getRatesTypical() {
		List<T> result = new ArrayList<>();
		for (R value : this) {
			result.add(value.getTypical());
		}
		return result;
	}

	public List<Double> getVolumes() {
		List<Double> result = new ArrayList<>();
		for (R value : this) {
			result.add(((RateCandle)value).getVolume());
		}
		return result;
	}

	public List<Float> getFVolumes() {
		List<Float> result = new ArrayList<>();
		for (R value : this) {
			result.add((float) ((RateCandle)value).getVolume());
		}
		return result;
	}

	public R first() {
		if (isEmpty())
			return null;
		return getFirst();
	}

	public R last() {
		if (isEmpty())
			return null;
		return getLast();
	}

	public  List<R> getNLastValues(int count) {
		if (count <= 0)
			return null;
		if (count >= this.size())
			return this;
		return this.subList(this.size() - count, this.size());
	}
}
