package com.pdp.shared.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestContextTest {

  @Test
  void 应以不可变值对象承载请求和审计上下文() {
    var workspaceId = new WorkspaceId(UUID.randomUUID());
    var actorId = new ActorId(UUID.randomUUID());
    var requestContext =
        new RequestContext(
            workspaceId,
            actorId,
            new TraceId("01JZTRACE8N4M2C6V7B9K0P3Q5R"),
            new IdempotencyKey("import-project-0001"));
    var auditContext =
        new AuditContext(requestContext, Instant.parse("2026-07-17T08:00:00Z"), "10.0.0.8");

    assertThat(auditContext.workspaceId()).isEqualTo(workspaceId);
    assertThat(auditContext.actorId()).isEqualTo(actorId);
    assertThat(auditContext.traceId().value()).startsWith("01JZ");
    assertThat(auditContext.idempotencyKey()).get().extracting(IdempotencyKey::value)
        .isEqualTo("import-project-0001");
    assertThat(RequestContext.query(workspaceId, actorId, requestContext.traceId()).idempotencyKey())
        .isEmpty();
  }

  @Test
  void 应拒绝空白或超长幂等键() {
    assertThatThrownBy(() -> new IdempotencyKey(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotencyKey("too-short"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotencyKey("x".repeat(129)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
