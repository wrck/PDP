package com.pdp.shared.context;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 审计上下文值对象。
 *
 * <p>对应数据模型 {@code AuditEvent} 写入所需字段：
 * {@code occurred_at}、{@code actor_ref}、{@code workspace_id}、{@code action}、
 * {@code target_ref}、{@code result}、{@code reason}、{@code before_digest}、
 * {@code after_digest}、{@code trace_id}、{@code source_ip}、{@code metadata}。
 *
 * <p>审计采用只追加和摘要链；敏感值只保存摘要或脱敏快照，不进入日志。
 */
public record AuditContext(
        Instant occurredAt,
        ActorRef actorRef,
        WorkspaceId workspaceId,
        String action,
        ObjectRef targetRef,
        String result,
        String reason,
        String beforeDigest,
        String afterDigest,
        UUID traceId,
        String sourceIp,
        Map<String, Object> metadata) {

    public AuditContext {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt 不能为 null");
        }
        if (actorRef == null) {
            throw new IllegalArgumentException("actorRef 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 对象引用值对象，对应 {@code ObjectRef}：
     * {@code object_type_key}、{@code object_id}、{@code workspace_id}。
     */
    public record ObjectRef(String objectTypeKey, UUID objectId, WorkspaceId workspaceId) {
        public ObjectRef {
            if (objectTypeKey == null || objectTypeKey.isBlank()) {
                throw new IllegalArgumentException("objectTypeKey 不能为空");
            }
            if (objectId == null) {
                throw new IllegalArgumentException("objectId 不能为 null");
            }
            if (workspaceId == null) {
                throw new IllegalArgumentException("workspaceId 不能为 null");
            }
        }
    }
}
