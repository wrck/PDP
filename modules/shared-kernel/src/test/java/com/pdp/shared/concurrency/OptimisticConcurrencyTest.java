package com.pdp.shared.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OptimisticConcurrencyTest {

  @Test
  void 应在revision一致时生成下一版本和强ETag() {
    var current = new Revision(7);

    OptimisticConcurrencyGuard.requireMatch(new Revision(7), current);

    assertThat(current.next()).isEqualTo(new Revision(8));
    assertThat(EntityTag.from(current).value()).isEqualTo("\"7\"");
    assertThat(EntityTag.parse("\"7\"").revision()).isEqualTo(current);
  }

  @Test
  void 应将过期revision转换为稳定409冲突() {
    assertThatThrownBy(
            () ->
                OptimisticConcurrencyGuard.requireMatch(new Revision(6), new Revision(7)))
        .isInstanceOf(ConcurrencyConflictException.class)
        .satisfies(
            error ->
                assertThat(((ConcurrencyConflictException) error).errorCode())
                    .isEqualTo(com.pdp.shared.error.ErrorCode.REVISION_CONFLICT));
  }
}
