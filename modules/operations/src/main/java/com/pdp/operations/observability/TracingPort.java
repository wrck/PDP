package com.pdp.operations.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 分布式追踪端口（六边形架构出站端口）。
 *
 * <p>对应 spec.md 可观测性基线和 SC-015（失败事项可追踪率 100%）。屏蔽具体追踪框架
 * （OpenTelemetry/Brave/Zipkin），业务模块通过此端口创建和管理追踪 Span。
 *
 * <p><strong>关联 ID 传播</strong>：所有跨服务调用 MUST 传播 correlationId 和 traceId，
 * 保证调用链可关联（SC-015 失败可追踪）。
 *
 * <p><strong>Span 语义</strong>：
 * <ul>
 *   <li>请求入口创建根 Span（{@link #startSpan}）；</li>
 *   <li>下游调用创建子 Span（{@link #startChildSpan}）；</li>
 *   <li>Span 携带业务标签（工作空间、操作、服务类别）；</li>
 *   <li>Span 完成时记录结果（成功/失败）和异常。</li>
 * </ul>
 *
 * <p>实现由 {@code public-persistence} 或独立 infrastructure 模块提供
 * （OpenTelemetry 适配器）。
 */
public interface TracingPort {

    /**
     * 启动根 Span（请求入口）。
     *
     * @param operation 操作标识（如 {@code project.advance}）
     * @return 追踪 Span 句柄
     */
    TraceSpan startSpan(String operation);

    /**
     * 启动子 Span（下游调用或内部步骤）。
     *
     * @param parent   父 Span
     * @param operation 子操作标识
     * @return 子 Span
     */
    TraceSpan startChildSpan(TraceSpan parent, String operation);

    /**
     * 完成 Span（记录结果）。
     *
     * @param span     Span
     * @param succeeded 是否成功
     * @param error     异常（成功时为 null）
     */
    void endSpan(TraceSpan span, boolean succeeded, Throwable error);

    /**
     * 为 Span 添加标签（业务上下文）。
     *
     * @param span  Span
     * @param key   标签键
     * @param value 标签值
     */
    void tagSpan(TraceSpan span, String key, String value);

    /**
     * 记录 Span 事件（业务里程碑）。
     *
     * @param span        Span
     * @param event       事件名
     * @param attributes  事件属性
     */
    void recordEvent(TraceSpan span, String event, Map<String, String> attributes);

    /**
     * 获取当前线程的关联 ID（用于日志关联）。
     *
     * @return 关联 ID，无活跃 Span 时生成新 ID
     */
    UUID currentCorrelationId();

    /**
     * 追踪 Span 句柄值对象。
     *
     * @param spanId        Span ID
     * @param traceId       追踪 ID（整个调用链共享）
     * @param parentSpanId  父 Span ID（根 Span 为 null）
     * @param operation     操作标识
     * @param startedAt     开始时间
     * @param correlationId 关联 ID（用于日志关联）
     */
    record TraceSpan(
            UUID spanId,
            UUID traceId,
            UUID parentSpanId,
            String operation,
            Instant startedAt,
            UUID correlationId) {

        public TraceSpan {
            Objects.requireNonNull(spanId, "spanId 不能为 null");
            Objects.requireNonNull(traceId, "traceId 不能为 null");
            Objects.requireNonNull(operation, "operation 不能为 null");
            if (operation.isBlank()) {
                throw new IllegalArgumentException("operation 不能为空白");
            }
            Objects.requireNonNull(startedAt, "startedAt 不能为 null");
            Objects.requireNonNull(correlationId, "correlationId 不能为 null");
        }

        /** 是否为根 Span。 */
        public boolean isRoot() {
            return parentSpanId == null;
        }

        /** 计算已耗时。 */
        public Duration elapsed(Instant now) {
            return Duration.between(startedAt, now);
        }
    }
}
