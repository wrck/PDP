package com.pdp.operations.observability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 可用性日度汇总记录（数据模型对象，对应 AvailabilityDailySummary）。
 *
 * <p>对应 spec.md 数据模型 "OperationalAlert / RecoveryOperation / AvailabilityDailySummary：
 * P1 告警、恢复和可用性证据对象"。由 {@link SliCollector#computeDailySummary} 生成，
 * 持久化后用于月度可审计报告（SC-037）和可用性验收（SC-016）。
 *
 * @param id                 记录 ID（UUIDv7）
 * @param serviceCategory    服务类别
 * @param date               汇总日期（UTC）
 * @param totalCount         总请求数
 * @param qualifiedCount     合格请求数（FR-165）
 * @param availableCount     可用请求数（成功完成且未超时）
 * @param excludedCount      排除请求数
 * @param failedCount        失败请求数
 * @param timeoutCount       超时请求数
 * @param availabilityPercent 可用性百分比（availableCount ÷ qualifiedCount × 100）
 * @param meetsSlo           是否满足 SLO（SC-016）
 * @param p50Latency         P50 延迟
 * @param p95Latency         P95 延迟（SC-018）
 * @param p99Latency         P99 延迟
 * @param computedAt         计算时间
 */
public record AvailabilityDailySummary(
        UUID id,
        ServiceCategory serviceCategory,
        LocalDate date,
        long totalCount,
        long qualifiedCount,
        long availableCount,
        long excludedCount,
        long failedCount,
        long timeoutCount,
        double availabilityPercent,
        boolean meetsSlo,
        Duration p50Latency,
        Duration p95Latency,
        Duration p99Latency,
        Instant computedAt) {

    public AvailabilityDailySummary {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(serviceCategory, "serviceCategory 不能为 null");
        Objects.requireNonNull(date, "date 不能为 null");
        Objects.requireNonNull(computedAt, "computedAt 不能为 null");
        if (qualifiedCount < 0 || availableCount < 0 || excludedCount < 0
                || failedCount < 0 || timeoutCount < 0 || totalCount < 0) {
            throw new IllegalArgumentException("计数不能为负");
        }
    }

    /** 可用性比率（0.0~1.0）。 */
    public double availabilityRatio() {
        if (qualifiedCount == 0) {
            return 1.0;
        }
        return (double) availableCount / qualifiedCount;
    }

    /** 失败率（0.0~1.0）。 */
    public double failureRate() {
        if (qualifiedCount == 0) {
            return 0.0;
        }
        return (double) failedCount / qualifiedCount;
    }
}
