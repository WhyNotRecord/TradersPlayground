package ru.rexchange.tools;

import ru.rexchange.exception.UserException;

import java.util.*;

public class TimeUtils {//todo SyncUtils?
  public static final int HOUR_IN_SECONDS = 3600;
  public static final long HOUR_IN_MS = HOUR_IN_SECONDS * 1000L;
  private static final List<Integer> ValidPeriods = new ArrayList<>();
  static {
    ValidPeriods.add(60);
    ValidPeriods.add(300);
    //ValidPeriods.add(600);
    ValidPeriods.add(900);
    //ValidPeriods.add(1800);
    ValidPeriods.add(3600);
    ValidPeriods.add(3600 * 4);
    ValidPeriods.add(3600 * 24);
    ValidPeriods.add(3600 * 24 * 7);//для расчёта долгосрочных трендов
  }
  private static final Map<Integer, String> PeriodSymbols = new HashMap<>();
  static {
    PeriodSymbols.put(60, "1m");
    PeriodSymbols.put(300, "5m");
    //PeriodSymbols.put(600, "10m");
    PeriodSymbols.put(900, "15m");
    //PeriodSymbols.put(1800, "30m");
    PeriodSymbols.put(3600, "1h");
    PeriodSymbols.put(3600 * 4, "4h");
    PeriodSymbols.put(3600 * 24, "1d");
    PeriodSymbols.put(3600 * 24 * 7, "1w");
  }

  /**
   * Определяет время до начала ближайшего периода в N миллисекунд
   * @param periodMs - period length in milliseconds
   * @return next period start time
   */
  public static long getTimeToNextPeriodStart(long periodMs) {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    long now = cal.getTimeInMillis();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long dayStart = cal.getTimeInMillis();
    long passed = (now - dayStart) % periodMs;
    return periodMs - passed;
  }

  public static long getPrevPeriodStart(long periodMs) {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    long now = cal.getTimeInMillis();
    cal.set(Calendar.ZONE_OFFSET, 0);//GMT
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long dayStart = cal.getTimeInMillis();
    long passed = (now - dayStart) % periodMs;
    return now - passed;
  }

  public static long getPrevPeriodStart(long periodMs, long now) {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    cal.set(Calendar.ZONE_OFFSET, 0);//GMT
    cal.setTimeInMillis(now);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long dayStart = cal.getTimeInMillis();
    long passed = (now - dayStart) % periodMs;
    return now - passed;
  }

  public static String getPeriodSymbol(int period) {
    return PeriodSymbols.getOrDefault(period, null);
  }

  public static Integer getLargerPeriod(int period) {
    if (!ValidPeriods.contains(period))
      throw new UserException("Invalid period value: " + period);
    int ind = ValidPeriods.indexOf(period) + 1;
    if (ind == ValidPeriods.size())
      return null;
    return ValidPeriods.get(ind);
  }

  public static Integer getSmallerPeriod(int period) {
    if (!ValidPeriods.contains(period))
      throw new UserException("Invalid period value: " + period);
    int ind = ValidPeriods.indexOf(period) - 1;
    if (ind <= 0)
      return null;
    return ValidPeriods.get(ind);
  }

  public static int getSmallerPeriodSafe(int period) {
    Integer smallerPeriod = getSmallerPeriod(period);
    if (smallerPeriod == null)
      return 1;
    return smallerPeriod;
  }

  public static long getMomentNPeriodsAgo(long period, int count) {
    long nowRound = getPrevPeriodStart(period);
    return nowRound - (period * count);
  }

  public static long getMomentNPeriodsAgo(long periodMs, int count, long now) {
    long nowRound = getPrevPeriodStart(periodMs, now);
    return nowRound - (periodMs * count);
  }

  public static List<Long> getLastPeriodsNet(long period, int count) {
    List<Long> result = new LinkedList<>();
    long moment = getMomentNPeriodsAgo(period, count);
    result.add(moment);
    for (int i = count - 1; i > 0; i--) {
      moment += period;
      result.add(moment);
    }
    return result;
  }
}
