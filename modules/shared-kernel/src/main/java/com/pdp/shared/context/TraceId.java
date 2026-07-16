package com.pdp.shared.context;

public record TraceId(String value) {
  public TraceId {
    value = requireText(value, "traceId");
    if (value.length() > 128) {
      throw new IllegalArgumentException("traceId 不能超过 128 个字符");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
    return value;
  }
}
