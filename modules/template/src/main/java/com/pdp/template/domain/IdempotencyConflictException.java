package com.pdp.template.domain;

import com.pdp.shared.context.IdempotencyKey;

/** 同一工作空间和幂等键被用于不同项目创建请求。 */
public final class IdempotencyConflictException extends IllegalStateException {
  private final IdempotencyKey idempotencyKey;

  public IdempotencyConflictException(IdempotencyKey idempotencyKey) {
    super("幂等键已绑定到不同的项目创建请求");
    this.idempotencyKey = idempotencyKey;
  }

  public IdempotencyKey idempotencyKey() {
    return idempotencyKey;
  }
}
