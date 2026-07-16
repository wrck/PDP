package com.pdp.shared.context;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 审计写入所需的最小不可变上下文。 */
public record AuditContext(RequestContext requestContext, Instant occurredAt, String sourceIp) {
  public AuditContext {
    Objects.requireNonNull(requestContext, "requestContext 不能为空");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
    if (sourceIp == null || sourceIp.isBlank()) {
      throw new IllegalArgumentException("sourceIp 不能为空");
    }
  }

  public WorkspaceId workspaceId() {
    return requestContext.workspaceId();
  }

  public ActorId actorId() {
    return requestContext.actorId();
  }

  public TraceId traceId() {
    return requestContext.traceId();
  }

  public Optional<IdempotencyKey> idempotencyKey() {
    return requestContext.idempotencyKey();
  }
}
