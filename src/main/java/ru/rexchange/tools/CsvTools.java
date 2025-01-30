package ru.rexchange.tools;

import org.jetbrains.annotations.NotNull;
import ru.rexchange.data.storage.RateCandle;

import java.util.Locale;

public class CsvTools {
  public static String getHeaderRowString(char separator, String... names) {
    StringBuilder sb = new StringBuilder(names[0]);
    for (int i = 1; i < names.length; i++) {
      sb.append(separator).append(" ").append(names[i]);
    }
    return sb.toString();
  }

  public static String getRowString(char separator, Object... values) {
    StringBuilder sb = new StringBuilder(prepareValue(values[0]));
    for (int i = 1; i < values.length; i++) {
      sb.append(separator).append(" ").append(prepareValue(values[i]));
    }
    return sb.toString();
  }

  /*private static String prepareValue(Object value) {
    if (value instanceof Double)//todo нельзя ли как-то получше?
      value = ((Double) value).floatValue();
    if (value instanceof Float) {
      float fValue = (float) value;
      float absValue = Math.abs(fValue);
      if (absValue > 1000000000f)
        return String.valueOf((int) fValue);
      if (absValue > 10000000f)
        return String.format(Locale.ROOT, "%.1f", fValue);
      if (absValue > 100f)
        return String.valueOf(value);
      return String.format(Locale.ROOT, "%.5f", value);
    }
    return String.valueOf(value);
  }*/

  private static String prepareValue(Object value) {
    double abs;
    if (value instanceof Double)
      abs = Math.abs((Double) value);
    else if (value instanceof Float)
      abs = Math.abs((Float) value);
    else if (value instanceof Long)
      abs = (double) Math.abs((Long) value);
    else if (value instanceof Integer)
      abs = Math.abs((Integer) value);
    else
      return String.valueOf(value);
    if (value instanceof Float) {
      float fValue = (float) value;
      return getFloatingPointString(value, abs, fValue);
    } else if (value instanceof Double) {
      double fValue = (double) value;
      return getFloatingPointString(value, abs, fValue);
    }
    return String.valueOf(value);
  }

  @NotNull
  private static String getFloatingPointString(Object value, double abs, Object fValue) {
    if (abs > 1000000000)
      return String.format(Locale.ROOT, "%.0f", fValue);
    if (abs > 10000000)
      return String.format(Locale.ROOT, "%.1f", fValue);
    if (abs > 100)
      return String.format(Locale.ROOT, "%.2f", value);
    if (abs > 0.01 || abs == 0.0)
      return String.format(Locale.ROOT, "%.5f", value);
    return String.format(Locale.ROOT, "%.7f", value);
  }

  public static RateCandle prepareCandle(String[] candleValues) {
    long ts = Long.parseLong(candleValues[0]);
    long ets = Long.parseLong(candleValues[1]);
    double open = Double.parseDouble(candleValues[2]);
    double close = Double.parseDouble(candleValues[3]);
    double low = Double.parseDouble(candleValues[4]);
    double high = Double.parseDouble(candleValues[5]);
    double volume = Double.parseDouble(candleValues[6]);
    double qVolume = Double.parseDouble(candleValues[7]);

    return new RateCandle(ts, ets, low, high, open, close, volume, qVolume);
  }

  public static String exportGridAsCsv(Float[][] array) {
    StringBuilder sb = new StringBuilder();
    for (Float[] floats : array) {
      String row = getRowString(',', (Object[]) floats);
      sb.append(row).append(System.lineSeparator());
    }
    return sb.toString();
  }

  public static String toCSVLine(String[] values, char separator) {
    if (values == null || values.length == 0)
      return "";
    StringBuilder sb = new StringBuilder(String.valueOf(values[0]));
    for (int i = 1; i < values.length; i++) {
      sb.append(separator).append(values[i]);
    }
    return sb.toString();
  }
}
