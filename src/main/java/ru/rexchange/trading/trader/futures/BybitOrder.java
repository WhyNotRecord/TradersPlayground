package ru.rexchange.trading.trader.futures;

import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.trade.PositionIdx;

import java.math.BigDecimal;
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

  public PositionIdx getPositionIdx() {
    return PositionIdx.valueOf(getString("positionIdx"));
  }

  //TODO implement all the getters
  public String getSymbol() {
    return null;
  }

  public BigDecimal getAvgPrice() {
    return null;
  }

  public BigDecimal getExecutedQty() {
    return null;
  }

  public Number getPrice() {
    return null;
  }

  public Number getStopPrice() {
    return null;
  }

  public String getStatus() {
    return null;
  }

  public Number getOrigQty() {
    return null;
  }

  public long getUpdateTime() {
    return 0;
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
