package com.pdp.shared.context;

import java.util.Objects;
import java.util.Optional;

/** 请求范围内的工作空间、操作者、链路与幂等上下文。 */
public record RequestContext(
    WorkspaceId workspaceId,
    ActorId actorId,
    TraceId traceId,
    Optional<IdempotencyKey> idempotencyKey) {
  public RequestContext {
    Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
    Objects.requireNonNull(actorId, "actorId 不能为空");
    Objects.requireNonNull(traceId, "traceId 不能为空");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为空");
  }

  public RequestContext(
      WorkspaceId workspaceId,
      ActorId actorId,
      TraceId traceId,
      IdempotencyKey idempotencyKey) {
    this(workspaceId, actorId, traceId, Optional.of(idempotencyKey));
  }

  public static RequestContext query(
      WorkspaceId workspaceId, ActorId actorId, TraceId traceId) {
    return new RequestContext(workspaceId, actorId, traceId, Optional.empty());
  }
}
