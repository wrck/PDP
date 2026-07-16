package com.pdp.shared.concurrency;

import java.util.Objects;

public final class OptimisticConcurrencyGuard {
  private OptimisticConcurrencyGuard() {}

  public static void requireMatch(Revision expected, Revision actual) {
    Objects.requireNonNull(expected, "expectedRevision 不能为空");
    Objects.requireNonNull(actual, "actualRevision 不能为空");
    if (!expected.equals(actual)) {
      throw new ConcurrencyConflictException(expected, actual);
    }
  }
}
