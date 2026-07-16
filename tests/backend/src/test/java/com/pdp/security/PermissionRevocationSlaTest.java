package com.pdp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.identity.application.AuthorizationRevoked;
import com.pdp.identity.application.PermissionRevocationSla;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionRevocationSlaTest {

  @Test
  void 权限撤销传播期限必须满足P1基线() {
    var occurredAt = Instant.parse("2026-07-17T08:00:00Z");
    var event =
        AuthorizationRevoked.create(
            UUID.randomUUID(), 12, "撤销项目访问权", occurredAt, PermissionRevocationSla.p1());

    assertThat(event.localCacheDeadline()).isEqualTo(occurredAt.plusSeconds(5));
    assertThat(event.searchProjectionDeadline()).isEqualTo(occurredAt.plusSeconds(30));
    assertThat(event.realtimeSessionDeadline()).isEqualTo(occurredAt.plusSeconds(30));
    assertThat(event.sessionRevocationDeadline()).isEqualTo(occurredAt.plusSeconds(60));
  }
}
