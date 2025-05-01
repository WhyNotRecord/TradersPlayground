package ru.rexchange.trading.trader.futures;

import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.PositionIdx;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

public class BybitOrder {
  private final Map<String, Object> orderData;

  /**
   * Конструктор принимает словарь со сведениями об ордере.
   *
   * @param orderData словарь с данными ордера
   */
  public BybitOrder(Map<String, Object> orderData) {
    this.orderData = orderData;
  }

  /**
   * Возвращает строковое значение по заданному ключу.
   *
   * @param key ключ для извлечения значения
   * @return строковое представление значения или null, если значение отсутствует
   */
  public String getString(String key) {
    Object value = orderData.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * Возвращает значение типа Float по заданному ключу.
   * Если значение невозможно привести к Float, возвращает null.
   *
   * @param key ключ для извлечения значения
   * @return число типа Float или null, если значение отсутствует или некорректно
   */
  public Float getFloat(String key) {
    Object value = orderData.get(key);
    if (value == null) {
      return null;
    }
    try {
      if (value instanceof Number) {
        return ((Number) value).floatValue();
      } else {
        return Float.parseFloat(value.toString());
      }
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Возвращает значение типа Double по заданному ключу.
   * Если значение невозможно привести к Double, возвращает null.
   *
   * @param key ключ для извлечения значения
   * @return число типа Double или null, если значение отсутствует или некорректно
   */
  public Double getDouble(String key) {
    Object value = orderData.get(key);
    if (value == null) {
      return null;
    }
    try {
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      } else {
        return Double.parseDouble(value.toString());
      }
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Возвращает значение типа Integer по заданному ключу.
   * Если значение невозможно привести к Integer, возвращает null.
   *
   * @param key ключ для извлечения значения
   * @return число типа Integer или null, если значение отсутствует или некорректно
   */
  public Integer getInteger(String key) {
    Object value = orderData.get(key);
    if (value == null) {
      return null;
    }
    try {
      if (value instanceof Number) {
        return ((Number) value).intValue();
      } else {
        return Integer.parseInt(value.toString());
      }
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public BigDecimal getBigDecimal(String key) {
    Object value = orderData.get(key);
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    } else if (value instanceof Number) {
      return BigDecimal.valueOf(((Number) value).doubleValue());
    } else if (value instanceof String) {
      try {
        return new BigDecimal((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Возвращает булево значение по заданному ключу.
   * Если значение невозможно привести к Boolean, возвращает null.
   *
   * @param key ключ для извлечения значения
   * @return булево значение или null, если значение отсутствует или некорректно
   */
  public Boolean getBoolean(String key) {
    Object value = orderData.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    } else {
      return Boolean.parseBoolean(value.toString());
    }
  }


  public String getSide() {
    return getString("side");
  }

  public String getOrderId() {
    return getString("orderId");
  }

  public String getType() {
    return getString("orderType");
    //return TradeOrderType.valueOf(getString("orderType"));
  }

  public Integer getPositionIdx() {
    //return PositionIdx.valueOf(getString("positionIdx"));
    return getInteger("positionIdx");
    /*Integer value = getInteger("positionIdx"); // Получаем значение как Integer
    if (value != null) {
      return Arrays.stream(PositionIdx.values())
          .filter(idx -> idx.getIndex() == value)
          .findFirst()
          .orElse(null);
    } else {
      return null;
    }*/
  }

  public String getSymbol() {
    return getString("symbol");
  }

  public BigDecimal getAvgPrice() {
    return getBigDecimal("avgPrice");//String
  }

  public Double getOrigQty() {
    return getDouble("qty");
  }

  public BigDecimal getExecutedQty() {
    return getBigDecimal("cumExecQty");//String
  }

  public Float getPrice() {
    return getFloat("price"); // price из FIELDS
  }

  public Float getStopPrice() {
    return getFloat("triggerPrice"); // price из FIELDS
  }

  public String getStatus() {
    return getString("orderStatus"); // orderStatus из FIELDS
  }

  public long getUpdateTime() {
    Object value = orderData.get("updatedTime"); // updatedTime из FIELDS
    if (value instanceof Number) {
      return ((Number) value).longValue();
    } else if (value instanceof String) {
      try {
        return Long.parseLong((String) value);
      } catch (NumberFormatException e) {
        return 0; // or handle the error as needed
      }
    }

    return 0;
  }

  public Integer getTriggerType() {
    return getInteger("triggerDirection");
  }

  /**
   * Перечень ключей, которые следует выводить в методе toString().
   */
  private static final String[] FIELDS = {
      "symbol",
      "side",
      "orderType",
      "orderId",
      "orderStatus",
      "price",
      "qty",
      "avgPrice",
      "cumExecQty",
      "createdTime",
      "positionIdx",
      "stopOrderType",
      "triggerPrice",
      "timeInForce",
      "updatedTime",
      "cancelType",
      "lastPriceOnCreated"
  };

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("BybitOrder{");
    boolean isFirst = true;
    for (String field : FIELDS) {
      if (!isFirst) {
        sb.append(", ");
      }
      sb.append(field).append("=").append(getString(field));
      isFirst = false;
    }
    sb.append("}");
    return sb.toString();
  }
}
