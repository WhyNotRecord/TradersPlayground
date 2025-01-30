package ru.rexchange.data.storage;

import java.util.Collection;
import java.util.Collections;

public abstract class AbstractRate<T extends Number> {
  public abstract T getValue();
  public abstract T getTypical();
  public abstract T getRelativeChange();
  public Collection<T> getUnifiedValues() {
    return Collections.singletonList(getRelativeChange());
  }
  public abstract long getOpenTime();
  public abstract long getCloseTime();
}
