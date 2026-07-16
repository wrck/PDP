package com.pdp.shared.context;

import java.util.UUID;

/**
 * 链路上下文值对象。
 *
 * <p>聚合 {@code trace_id} 与 {@code correlation_id}：trace_id 贯穿单次请求的分布式追踪，
 * correlation_id 贯穿请求、状态迁移、审计和事件，保证端到端可关联。
 */
public record TraceContext(UUID traceId, UUID correlationId) {

    public TraceContext {
        if (traceId == null) {
            throw new IllegalArgumentException("traceId 不能为 null");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId 不能为 null");
        }
    }

    /** 生成新的链路上下文（traceId 与 correlationId 均为新 UUIDv7）。 */
    public static TraceContext next() {
        UUID trace = com.pdp.shared.id.UuidV7Generator.next();
        return new TraceContext(trace, trace);
    }

    /** 派生新的 correlationId 但保留同一 traceId（用于子操作）。 */
    public TraceContext derive() {
        return new TraceContext(traceId, com.pdp.shared.id.UuidV7Generator.next());
    }
}
