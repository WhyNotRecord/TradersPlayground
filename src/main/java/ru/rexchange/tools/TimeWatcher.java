package ru.rexchange.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author WhyNot
 * @since 11.09.2017.
 */
public class TimeWatcher {
  public static final Long MILLISECONDS_DIVISOR = 1000000L;
  public static final Long MICROSECONDS_DIVISOR = 1000L;
  private static Logger LOGGER = null;
  private long divisor = 1;
  //private static TimeWatcher instance = null;
  private Map<String, List<Long>> container = new ConcurrentHashMap<>();
  private Map<CounterKey, Long> counters = new ConcurrentHashMap<>();

  public TimeWatcher() {
  }

  public TimeWatcher(boolean log) {
    if (log)
      initLogger();
  }

  public TimeWatcher(boolean log, long divisor) {
    if (log)
      initLogger();
    this.divisor = divisor;
  }

  protected static synchronized void initLogger() {
    if (LOGGER == null)
      LOGGER = LoggerFactory.getLogger(TimeWatcher.class);
  }

  /*public static TimeWatcher instance() {
    if (instance == null) {
      instance = new TimeWatcher();
    }
    return instance;
  }*/

  public CounterKey start(String label) {
    long time = System.nanoTime();
    CounterKey key = new CounterKey(label, time);
    counters.put(key, time);
    return key;
  }

  public CounterKey startSafe(String label) {
    try {
      return start(label);
    } catch (ConcurrentModificationException e) {
      return null;
    }
  }

  public Long stop(CounterKey key) {
    try {
      if (counters.containsKey(key)) {
        long period = System.nanoTime() - counters.get(key);
        add(key.label, period);
        counters.remove(key);
        return period / divisor;
      } else {
        if (LOGGER != null)
          LOGGER.debug(String.format("Unsuccessful stop call for label %s", key));
      }
    } catch (Exception e) {
      LOGGER.warn("Error", e);
    }
    return 0L;
  }

  public void stopSafe(CounterKey key) {
    try {
      stop(key);
    } catch (ConcurrentModificationException ignored) {
    }
  }

  public void stop(String label) {
    stop(new CounterKey(label));
  }

  @Deprecated
  public void add(String label, Long period) {
    try {
      //period /= divisor;
      if (!container.containsKey(label)) {
        container.put(label, Collections.synchronizedList(new LinkedList<>()));
      }
      container.get(label).add(period);
      if (LOGGER != null)
        LOGGER.debug(String.format("New sample for label %s: %,d", label, period / divisor));
    } catch (Exception e) {
      LOGGER.warn("Error", e);
    }
  }

  public Long getAverage(String label) {
    try {
      if (!container.containsKey(label))
        return 0L;
      Long result = 0L;
      for (Long value : container.get(label)) {
        result += value;
      }
      result /= container.get(label).size();
      if (LOGGER != null)
        LOGGER.debug(String.format("Average value for label %s is %,d", label, result  / divisor));
      return result;
    } catch (Exception e) {
      LOGGER.warn("Error", e);
      return 0L;
    }
  }

  public Long getSum(String label) {
    try {
      if (!container.containsKey(label))
        return 0L;
      Long result = 0L;
      for (Long value : container.get(label)) {
        result += value;
      }
      if (LOGGER != null)
        LOGGER.debug(String.format("Full value for label %s is %,d", label, result  / divisor));
      return result;
    } catch (Exception e) {
      LOGGER.warn("Error", e);
      return 0L;
    }
  }

  public List<Long> getAverages() {
    return container.keySet().stream().
        map(this::getAverage).
        collect(Collectors.toList());
  }

  public List<Long> getSums() {
    return container.entrySet().stream().
        filter(key -> key.getValue().size() > 1).
        map(key -> getSum(key.getKey())).
        collect(Collectors.toList());
  }

  public void reset() {
    container.clear();
    counters.clear();
  }

  public static class CounterKey {
    private String label;
    private Long time;

    public CounterKey(String label, long time) {
      this.label = label;
      this.time = time;
    }

    public CounterKey(String label) {
      this.label = label;
      this.time = -1L;
    }

    public String getLabel() {
      return label;
    }

    @Override
    public boolean equals(Object obj) {
      return !(obj == null || !(obj instanceof CounterKey)) &&
          ((CounterKey) obj).label.equals(this.label) &&
          (((CounterKey) obj).time == -1 || this.time == -1 || ((CounterKey) obj).time.equals(this.time));
    }

    @Override
    public int hashCode() {
      return label.hashCode();
    }
  }
}
