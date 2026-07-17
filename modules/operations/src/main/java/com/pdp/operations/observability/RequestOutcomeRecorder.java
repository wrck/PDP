package com.pdp.operations.observability;

import com.pdp.shared.context.WorkspaceId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 请求结果记录器（协调 SLI 采集、指标采集和日志记录，FR-165）。
 *
 * <p>对应 FR-165 和 SC-015/SC-018。在请求处理完成后调用 {@link #record}，
 * 统一采集请求结果到三个可观测性通道：
 * <ol>
 *   <li>{@link SliCollector}：FR-165 可用性 SLI 采集（月度可用性计算）；</li>
 *   <li>{@link MetricsCollectorPort}：实时指标采集（Prometheus 风格，用于告警和仪表盘）；</li>
 *   <li>{@link StructuredLoggerPort}：结构化日志（含关联 ID，用于排障和审计）。</li>
 * </ol>
 *
 * <p><strong>排除项证据</strong>（SC-037）：排除请求 MUST 携带证据，{@link #recordExcluded}
 * 校验后采集。排除项证据完整率 MUST 为 100%。
 *
 * <p><strong>降级</strong>（FR-106）：SLI 采集异常 MUST 不影响核心操作，
 * 所有采集失败被捕获并降级为内部警告日志。
 */
public class RequestOutcomeRecorder {

    private final SliCollector sliCollector;
    private final MetricsCollectorPort metricsCollector;
    private final StructuredLoggerPort logger;

    public RequestOutcomeRecorder(SliCollector sliCollector,
                                  MetricsCollectorPort metricsCollector,
                                  StructuredLoggerPort logger) {
        this.sliCollector = Objects.requireNonNull(sliCollector, "sliCollector 不能为 null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector 不能为 null");
        this.logger = Objects.requireNonNull(logger, "logger 不能为 null");
    }

    /**
     * 记录成功请求结果。
     *
     * @param requestId       请求 ID
     * @param correlationId   关联 ID
     * @param workspaceId     工作空间
     * @param category        服务类别
     * @param operation       操作标识
     * @param actorId         操作者 ID
     * @param startedAt       开始时间
     * @param completedAt     完成时间
     */
    public void recordSuccess(UUID requestId, UUID correlationId, WorkspaceId workspaceId,
                              ServiceCategory category, String operation, UUID actorId,
                              Instant startedAt, Instant completedAt) {
        Objects.requireNonNull(requestId, "requestId 不能为 null");
        Objects.requireNonNull(category, "category 不能为 null");
        Objects.requireNonNull(operation, "operation 不能为 null");
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
        Objects.requireNonNull(completedAt, "completedAt 不能为 null");

        Duration duration = Duration.between(startedAt, completedAt);
        boolean withinTimeLimit = duration.compareTo(category.interactionTimeLimit()) <= 0;

        // 1. SLI 采集
        safeRecordSli(RequestOutcome.success(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt));

        // 2. 指标采集
        safeRecordMetrics(category, operation, true, duration, withinTimeLimit);

        // 3. 日志（超时时 WARN，正常时 DEBUG 减少噪音）
        if (!withinTimeLimit) {
            logger.warn(RequestOutcomeRecorder.class.getName(),
                    "request.timeout",
                    operation + " 耗时 " + duration.toMillis() + "ms 超过 "
                            + category.interactionTimeLimit().toMillis() + "ms 限额");
        }
    }

    /**
     * 记录失败请求结果。
     *
     * @param requestId       请求 ID
     * @param correlationId   关联 ID
     * @param workspaceId     工作空间
     * @param category        服务类别
     * @param operation       操作标识
     * @param actorId         操作者 ID
     * @param startedAt       开始时间
     * @param completedAt     完成时间
     * @param failureReason   失败原因稳定键
     * @param cause           异常（可为 null）
     */
    public void recordFailure(UUID requestId, UUID correlationId, WorkspaceId workspaceId,
                              ServiceCategory category, String operation, UUID actorId,
                              Instant startedAt, Instant completedAt,
                              String failureReason, Throwable cause) {
        Objects.requireNonNull(requestId, "requestId 不能为 null");
        Objects.requireNonNull(category, "category 不能为 null");
        Objects.requireNonNull(operation, "operation 不能为 null");
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
        Objects.requireNonNull(completedAt, "completedAt 不能为 null");
        Objects.requireNonNull(failureReason, "failureReason 不能为 null");

        Duration duration = Duration.between(startedAt, completedAt);

        // 1. SLI 采集
        safeRecordSli(RequestOutcome.failure(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt, failureReason));

        // 2. 指标采集
        safeRecordMetrics(category, operation, false, duration, false);

        // 3. 日志
        logger.error(RequestOutcomeRecorder.class.getName(),
                "request.failed",
                operation + " 失败: " + failureReason, cause);
    }

    /**
     * 记录排除请求结果（FR-165 排除，SC-037 证据）。
     *
     * @param requestId       请求 ID
     * @param correlationId   关联 ID
     * @param workspaceId     工作空间
     * @param category        服务类别
     * @param operation       操作标识
     * @param actorId         操作者 ID
     * @param startedAt       开始时间
     * @param completedAt     完成时间
     * @param succeeded       是否成功（排除请求可能成功也可能失败）
     * @param failureReason   失败原因（成功时为 null）
     * @param exclusionReason 排除原因
     * @param evidence        排除证据（MUST 非空，SC-037）
     */
    public void recordExcluded(UUID requestId, UUID correlationId, WorkspaceId workspaceId,
                               ServiceCategory category, String operation, UUID actorId,
                               Instant startedAt, Instant completedAt,
                               boolean succeeded, String failureReason,
                               ExclusionReason exclusionReason, String evidence) {
        Objects.requireNonNull(exclusionReason, "exclusionReason 不能为 null");
        Objects.requireNonNull(evidence, "evidence 不能为 null");
        if (evidence.isBlank()) {
            throw new IllegalArgumentException("排除证据不能为空白（SC-037）");
        }

        // SLI 采集（排除请求记录但不计入可用性分母）
        safeRecordSli(RequestOutcome.excluded(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt,
                succeeded, failureReason, exclusionReason, evidence));

        // 审计日志（排除请求 MUST 留证据）
        logger.audit(RequestOutcomeRecorder.class.getName(),
                "request.excluded",
                operation + " 排除原因: " + exclusionReason.stableKey() + ", 证据: " + evidence,
                correlationId, workspaceId, actorId,
                java.util.Map.of("exclusionReason", exclusionReason.stableKey(),
                        "evidence", evidence));
    }

    // ==================== 内部降级安全包装 ====================

    private void safeRecordSli(RequestOutcome outcome) {
        try {
            sliCollector.record(outcome);
        } catch (Exception e) {
            // 降级：SLI 采集失败不影响核心操作
            try {
                logger.warn(RequestOutcomeRecorder.class.getName(),
                        "sli.collection.failed",
                        "SLI 采集失败: " + e.getMessage());
            } catch (Exception ignored) {
                // 日志也失败，静默降级
            }
        }
    }

    private void safeRecordMetrics(ServiceCategory category, String operation,
                                   boolean succeeded, Duration duration, boolean withinTimeLimit) {
        try {
            metricsCollector.recordRequest(category, operation, succeeded, duration, withinTimeLimit);
        } catch (Exception e) {
            try {
                logger.warn(RequestOutcomeRecorder.class.getName(),
                        "metrics.collection.failed",
                        "指标采集失败: " + e.getMessage());
            } catch (Exception ignored) {
                // 静默降级
            }
        }
    }
}
