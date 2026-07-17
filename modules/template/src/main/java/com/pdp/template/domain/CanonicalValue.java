package com.pdp.template.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 对 JSON 兼容配置做深只读复制并生成与 Map 插入顺序无关的规范表示。 */
final class CanonicalValue {
  private CanonicalValue() {}

  static Map<String, Object> immutableMap(Map<String, ?> source, String label) {
    if (source == null) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    Map<String, Object> copied = new LinkedHashMap<>();
    new TreeMap<>(source).forEach((key, value) -> copied.put(requireKey(key), immutable(value)));
    return Collections.unmodifiableMap(copied);
  }

  static String canonical(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String text) {
      return "s" + text.length() + ":" + text;
    }
    if (value instanceof Boolean bool) {
      return bool ? "true" : "false";
    }
    if (value instanceof Number number) {
      return "n:" + normalizedNumber(number);
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder result = new StringBuilder("{");
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(requireKey(key), item));
      sorted.forEach(
          (key, item) -> result.append(canonical(key)).append('=').append(canonical(item)).append(';'));
      return result.append('}').toString();
    }
    if (value instanceof Collection<?> collection) {
      StringBuilder result = new StringBuilder("[");
      collection.forEach(item -> result.append(canonical(item)).append(';'));
      return result.append(']').toString();
    }
    throw new IllegalArgumentException("模板配置仅允许 JSON 兼容值: " + value.getClass().getName());
  }

  private static Object immutable(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Number number) {
      normalizedNumber(number);
      return value;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copied = new LinkedHashMap<>();
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(requireKey(key), item));
      sorted.forEach((key, item) -> copied.put(key, immutable(item)));
      return Collections.unmodifiableMap(copied);
    }
    if (value instanceof Collection<?> collection) {
      List<Object> copied = new ArrayList<>(collection.size());
      collection.forEach(item -> copied.add(immutable(item)));
      return Collections.unmodifiableList(copied);
    }
    throw new IllegalArgumentException("模板配置仅允许 JSON 兼容值: " + value.getClass().getName());
  }

  private static String normalizedNumber(Number value) {
    if ((value instanceof Double doubleValue && !Double.isFinite(doubleValue))
        || (value instanceof Float floatValue && !Float.isFinite(floatValue))) {
      throw new IllegalArgumentException("模板配置数字必须是有限值");
    }
    try {
      BigDecimal decimal =
          value instanceof BigInteger integer
              ? new BigDecimal(integer)
              : new BigDecimal(value.toString());
      BigDecimal normalized = decimal.stripTrailingZeros();
      return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("模板配置数字格式非法", exception);
    }
  }

  private static String requireKey(Object key) {
    if (!(key instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("模板配置键必须是非空字符串");
    }
    return text;
  }
}
