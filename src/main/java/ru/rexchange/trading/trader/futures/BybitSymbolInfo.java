package ru.rexchange.trading.trader.futures;

import java.util.Map;

/**
 * Контейнер-обёртка, содержащий словарь с ключами:
 * symbol, priceScale, priceFilter (словарь; внутри minPrice, maxPrice, tickSize),
 * lotSizeFilter (словарь; внутри minNotionalValue, maxOrderQty, maxMktOrderQty, minOrderQty, qtyStep),
 * leverageFilter(словарь; внутри minLeverage, maxLeverage, leverageStep), copyTrading

 * Пример:
 * {symbol=KASUSDT, contractType=LinearPerpetual, status=Trading, baseCoin=KAS, quoteCoin=USDT,
 * launchTime=1691138081000, deliveryTime=0, deliveryFeeRate=, priceScale=5,
 * leverageFilter={minLeverage=1, maxLeverage=50.00, leverageStep=0.01},
 * priceFilter={minPrice=0.00001, maxPrice=199.99998, tickSize=0.00001},
 * lotSizeFilter={maxOrderQty=4616000, minOrderQty=10, qtyStep=10, maxMktOrderQty=774000, minNotionalValue=5},
 * unifiedMarginTrade=true, fundingInterval=240, settleCoin=USDT, copyTrading=both,
 * upperFundingRate=0.01, lowerFundingRate=-0.01, riskParameters={priceLimitRatioX=0.15, priceLimitRatioY=0.3}}
 */
public class BybitSymbolInfo {
  private final Map<String, Object> symbolData;

  /**
   * Конструктор принимает словарь со сведениями об ордере.
   *
   * @param symbolData словарь с данными ордера
   */
  public BybitSymbolInfo(Map<String, Object> symbolData) {
    this.symbolData = symbolData;
  }

  /**
   * Возвращает строковое значение по заданному ключу.
   *
   * @param key ключ для извлечения значения
   * @return строковое представление значения или null, если значение отсутствует
   */
  public String getString(String key) {
    Object value = symbolData.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * Возвращает вложенный словарь по заданному ключу.
   *
   * @param key ключ для извлечения значения
   * @return вложенный словарь, хранящийся в базовом по ключу, иначе null
   */
  public Map<String, Object> getMap(String key) {
    Object value = symbolData.get(key);
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return null;
  }

  /**
   * Возвращает значение типа Float по заданному ключу.
   * Если значение невозможно привести к Float, возвращает null.
   *
   * @param key ключ для извлечения значения
   * @return число типа Float или null, если значение отсутствует или некорректно
   */
  public Float getFloat(String key) {
    Object value = symbolData.get(key);
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
    Object value = symbolData.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    } else {
      return Boolean.parseBoolean(value.toString());
    }
  }

  @Override
  public String toString() {
    return symbolData.toString();
  }

  public boolean contains(String key) {
    return symbolData.containsKey(key);
  }

  public String getSymbol() {
    return getString("symbol");
  }

  public String getQuoteAsset() {
    return getString("quoteCoin");
  }

  public String getBaseAsset() {
    return getString("baseCoin");
  }
}
