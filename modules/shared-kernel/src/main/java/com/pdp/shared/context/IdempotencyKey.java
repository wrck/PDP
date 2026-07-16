package com.pdp.shared.context;

public record IdempotencyKey(String value) {
  public IdempotencyKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("幂等键不能为空");
    }
    if (value.length() < 16) {
      throw new IllegalArgumentException("幂等键不能少于 16 个字符");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("幂等键不能超过 128 个字符");
    }
  }
}
