package com.pdp.operations.observability;

import com.pdp.shared.context.WorkspaceId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 结构化日志条目值对象。
 *
 * <p>对应 spec.md FR-069（关键审计事件记录）和可观测性基线。所有日志 MUST 结构化输出
 * （JSON），携带关联 ID、工作空间、操作者和业务上下文，便于检索和关联追踪。
 *
 * <p><strong>日志级别</strong>：
 * <ul>
 *   <li>{@link Level#TRACE}：详细调试（仅开发环境）；</li>
 *   <li>{@link Level#DEBUG}：调试信息；</li>
 *   <li>{@link Level#INFO}：常规业务事件；</li>
 *   <li>{@link Level#WARN}：可恢复异常或降级；</li>
 *   <li>{@link Level#ERROR}：错误（影响业务但未中断）；</li>
 *   <li>{@link Level#FATAL}：严重错误（服务不可用）。</li>
 * </ul>
 *
 * <p><strong>审计日志</strong>（FR-069）：业务、审批、配置、权限、登录、下载、导出、迁移和集成的
 * 关键审计事件 MUST 通过 {@link StructuredLoggerPort#audit} 记录，{@link #auditEvent} 为 true。
 *
 * @param timestamp     时间戳
 * @param level         日志级别
 * @param correlationId 关联 ID（跨服务调用链）
 * @param requestId     请求 ID
 * @param workspaceId   工作空间（null 表示平台级）
 * @param actorId       操作者 ID（null 表示系统调用）
 * @param logger        日志记录器名称（通常是类名）
 * @param event         事件标识（如 {@code project.created}）
 * @param message       日志消息
 * @param auditEvent    是否为审计事件（FR-069）
 * @param fields        附加结构化字段（业务上下文）
 * @param throwable     异常（可为 null）
 */
public record StructuredLogEntry(
        java.time.Instant timestamp,
        Level level,
        UUID correlationId,
        UUID requestId,
        WorkspaceId workspaceId,
        UUID actorId,
        String logger,
        String event,
        String message,
        boolean auditEvent,
        Map<String, Object> fields,
        Throwable throwable) {

    public StructuredLogEntry {
        Objects.requireNonNull(timestamp, "timestamp 不能为 null");
        Objects.requireNonNull(level, "level 不能为 null");
        Objects.requireNonNull(logger, "logger 不能为 null");
        if (logger.isBlank()) {
            throw new IllegalArgumentException("logger 不能为空白");
        }
        Objects.requireNonNull(message, "message 不能为 null");
        fields = fields != null ? Map.copyOf(fields) : Map.of();
    }

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

    public static StructuredLogEntry info(String logger, String event, String message) {
        return new StructuredLogEntry(java.time.Instant.now(), Level.INFO,
                null, null, null, null, logger, event, message, false, Map.of(), null);
    }

    public static StructuredLogEntry warn(String logger, String event, String message) {
        return new StructuredLogEntry(java.time.Instant.now(), Level.WARN,
                null, null, null, null, logger, event, message, false, Map.of(), null);
    }

    public static StructuredLogEntry error(String logger, String event, String message, Throwable t) {
        return new StructuredLogEntry(java.time.Instant.now(), Level.ERROR,
                null, null, null, null, logger, event, message, false, Map.of(), t);
    }

    public static StructuredLogEntry audit(String logger, String event, String message,
                                           UUID correlationId, WorkspaceId workspaceId,
                                           UUID actorId, Map<String, Object> fields) {
        return new StructuredLogEntry(java.time.Instant.now(), Level.INFO,
                correlationId, null, workspaceId, actorId, logger, event, message,
                true, fields, null);
    }

    public StructuredLogEntry withCorrelationId(UUID cid) {
        return new StructuredLogEntry(timestamp, level, cid, requestId, workspaceId,
                actorId, logger, event, message, auditEvent, fields, throwable);
    }

    public StructuredLogEntry withField(String key, Object value) {
        Map<String, Object> newFields = new java.util.HashMap<>(fields);
        newFields.put(key, value);
        return new StructuredLogEntry(timestamp, level, correlationId, requestId,
                workspaceId, actorId, logger, event, message, auditEvent, newFields, throwable);
    }
}
