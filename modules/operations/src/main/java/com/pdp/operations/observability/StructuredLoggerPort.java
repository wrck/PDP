package com.pdp.operations.observability;

import com.pdp.shared.context.WorkspaceId;

import java.util.Map;
import java.util.UUID;

/**
 * 结构化日志端口（六边形架构出站端口）。
 *
 * <p>对应 spec.md FR-069（关键审计事件记录）和可观测性基线。屏蔽具体日志框架
 * （SLF4J/Logback/Log4j2），业务模块通过此端口输出结构化日志和审计事件。
 *
 * <p><strong>审计事件</strong>（FR-069）：业务、审批、配置、权限、登录、下载、导出、迁移和集成的
 * 关键审计事件 MUST 通过 {@link #audit} 记录，保证 SC-008 审计覆盖率 100%。
 * 审计事件 MUST 持久化到审计存储（防篡改），不与普通日志混存。
 *
 * <p><strong>关联 ID 传播</strong>：所有日志 MUST 携带 correlationId（跨服务调用链），
 * 由 {@link TracingPort} 在请求入口生成并传播到下游调用。
 *
 * <p>实现由 {@code public-persistence} 或独立 infrastructure 模块提供（SLF4J 适配器 +
 * 审计存储适配器）。
 */
public interface StructuredLoggerPort {

    /**
     * 输出结构化日志。
     *
     * @param entry 日志条目
     */
    void log(StructuredLogEntry entry);

    /** 输出 INFO 级别日志。 */
    default void info(String logger, String event, String message) {
        log(StructuredLogEntry.info(logger, event, message));
    }

    /** 输出 WARN 级别日志。 */
    default void warn(String logger, String event, String message) {
        log(StructuredLogEntry.warn(logger, event, message));
    }

    /** 输出 ERROR 级别日志。 */
    default void error(String logger, String event, String message, Throwable t) {
        log(StructuredLogEntry.error(logger, event, message, t));
    }

    /**
     * 记录审计事件（FR-069）。
     *
     * <p>审计事件 MUST 持久化到审计存储（防篡改），不与普通日志混存。
     * 审计事件 MUST 携带操作者、工作空间、事件标识和业务上下文。
     *
     * @param logger        日志记录器名称
     * @param event         审计事件标识（如 {@code permission.granted}、{@code deliverable.downloaded}）
     * @param message       审计消息
     * @param correlationId 关联 ID
     * @param workspaceId   工作空间
     * @param actorId       操作者 ID
     * @param fields        附加业务上下文（如对象类型、对象 ID、操作前/后状态）
     */
    default void audit(String logger, String event, String message,
                       UUID correlationId, WorkspaceId workspaceId, UUID actorId,
                       Map<String, Object> fields) {
        log(StructuredLogEntry.audit(logger, event, message, correlationId,
                workspaceId, actorId, fields));
    }
}
