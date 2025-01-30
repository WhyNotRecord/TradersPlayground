package ru.rexchange.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple builder of Maps
 *
 * @author Victor Bellavin
 */
public final class MapBuilder<K, V> {

  public static <K, V> MapBuilder<K, V> es() {
    return new MapBuilder<K, V>();
  }

  public static <K, V> MapBuilder<K, V> es(K key, V value) {
    return new MapBuilder<K, V>().e(key, value);
  }

  public static MapBuilder<Object, Object> ops(Object key, Object value) {
    return new MapBuilder<Object, Object>().e(key, value);
  }

  public static MapBuilder<String, Object> ps() {
    return new MapBuilder<String, Object>();
  }

  public static MapBuilder<String, Object> params() {
    return ps();
  }

  public static Map<String, Object> param(String key, Object value) {
    return es(key, value).map;
  }

  private final Map<K, V> map;

  public MapBuilder(K key, V value) {
    this();
    e(key, value);
  }

  public MapBuilder() {
    this(new LinkedHashMap<K, V>());
  }

  public MapBuilder(Map<K, V> map) {
    super();
    this.map = map;
  }

  public MapBuilder<K, V> e(K key, V value) {
    map.put(key, value);
    return this;
  }

  public MapBuilder<K, V> e(boolean condition, K key, V value) {
    if (condition)
      map.put(key, value);
    return this;
  }

  public Map<K, V> map() {
    return map;
  }

  public Map<K, V> constMap() {
    return Collections.unmodifiableMap(map);
  }
}
