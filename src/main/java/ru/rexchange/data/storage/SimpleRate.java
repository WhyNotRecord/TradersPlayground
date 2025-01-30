package ru.rexchange.data.storage;

import ru.rexchange.tools.DateUtils;

import java.util.Date;

public class SimpleRate extends AbstractRate<Float> {
  private long openTime;
  private long closeTime;
  private float value;
  private SimpleRate previous = null;//todo заполнять при создании, тогда можно легко вычислять относительное движение

  public SimpleRate(float value, long openTime, long closeTime) {
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.value = value;
  }
  @Override
  public Float getValue() {
    return value;
  }

  @Override
  public Float getTypical() {
    return getValue();
  }

  @Override
  public Float getRelativeChange() {
    if (previous != null) {
      return (getValue() - previous.getValue()) / previous.getValue();
    }
    return null;
  }

  @Override
  public long getOpenTime() {
    return openTime;
  }

  @Override
  public long getCloseTime() {
    return closeTime;
  }

  public void setPrevious(SimpleRate rate) {
    this.previous = rate;
  }

  @Override
  public String toString() {
    try {
      return DateUtils.formatTimeMin(new Date(openTime)) + ": " + getValue();
    } catch (Exception e) {
      e.printStackTrace();
      return super.toString();
    }
  }

}
