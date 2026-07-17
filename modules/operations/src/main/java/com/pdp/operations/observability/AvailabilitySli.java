package com.pdp.operations.observability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 可用性 SLI 聚合结果（FR-165）。
 *
 * <p>对应 FR-165："月度可用性 = 成功完成且未超过适用交互时限的合格请求数 ÷ 合格请求总数"。
 * 本类封装按服务类别和时间段聚合的 SLI 计算结果，用于月度可用性报告（SC-016/SC-037）。
 *
 * <p><strong>计算口径</strong>：
 * <ul>
 *   <li><b>可用性</b> = {@link #availableCount} ÷ {@link #qualifiedCount}；</li>
 *   <li><b>合格请求</b> = 总请求 - 可排除请求（客户端取消、客户网络/身份故障）；</li>
 *   <li><b>可用请求</b> = 合格请求中成功完成且未超时的请求。</li>
 * </ul>
 *
 * @param serviceCategory    服务类别
 * @param periodStart        统计周期开始（月度或日度）
 * @param periodEnd          统计周期结束
 * @param totalCount         总请求数
 * @param qualifiedCount     合格请求数（排除可排除请求后）
 * @param availableCount     可用请求数（成功完成且未超时的合格请求）
 * @param excludedCount      排除请求数（客户端取消、客户网络/身份故障）
 * @param failedCount        失败请求数（合格请求中未成功）
 * @param timeoutCount       超时请求数（合格请求中成功但超时）
 * @param p50Latency         P50 延迟
 * @param p95Latency         P95 延迟（SC-018 验收）
 * @param p99Latency         P99 延迟
 * @param computedAt         计算时间
 */
public record AvailabilitySli(
        ServiceCategory serviceCategory,
        Instant periodStart,
        Instant periodEnd,
        long totalCount,
        long qualifiedCount,
        long availableCount,
        long excludedCount,
        long failedCount,
        long timeoutCount,
        Duration p50Latency,
        Duration p95Latency,
        Duration p99Latency,
        Instant computedAt) {

    public AvailabilitySli {
        Objects.requireNonNull(serviceCategory, "serviceCategory 不能为 null");
        Objects.requireNonNull(periodStart, "periodStart 不能为 null");
        Objects.requireNonNull(periodEnd, "periodEnd 不能为 null");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEnd 必须晚于 periodStart");
        }
        if (qualifiedCount < 0 || availableCount < 0 || excludedCount < 0
                || failedCount < 0 || timeoutCount < 0) {
            throw new IllegalArgumentException("计数不能为负");
        }
        if (availableCount > qualifiedCount) {
            throw new IllegalArgumentException("可用请求数不能超过合格请求数");
        }
    }

    /**
     * 可用性比率（0.0~1.0，FR-165）。
     *
     * <p>= availableCount ÷ qualifiedCount。合格请求数为 0 时返回 1.0（无请求视为完全可用）。
     */
    public double availabilityRatio() {
        if (qualifiedCount == 0) {
            return 1.0;
        }
        return (double) availableCount / qualifiedCount;
    }

    /** 可用性百分比（0.0~100.0）。 */
    public double availabilityPercent() {
        return availabilityRatio() * 100.0;
    }

    /** 是否满足 SLO（SC-016：核心 ≥ 99.95%，其他 ≥ 99.9%）。 */
    public boolean meetsSlo() {
        double target = serviceCategory.isCore() ? 99.95 : 99.9;
        return availabilityPercent() >= target;
    }

    /** 失败率（0.0~1.0）。 */
    public double failureRate() {
        if (qualifiedCount == 0) {
            return 0.0;
        }
        return (double) failedCount / qualifiedCount;
    }

    /** 超时率（0.0~1.0）。 */
    public double timeoutRate() {
        if (qualifiedCount == 0) {
            return 0.0;
        }
        return (double) timeoutCount / qualifiedCount;
    }

    /** 排除率（0.0~1.0）。 */
    public double exclusionRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) excludedCount / totalCount;
    }

    /** 统计周期对应的日期（用于日度汇总）。 */
    public LocalDate periodDate() {
        return periodStart.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }

    /**
     * 累加两个 SLI 结果（用于日度→月度聚合）。
     *
     * <p>延迟分位数取加权平均（简化，精确聚合需保留原始样本）。
     */
    public AvailabilitySli merge(AvailabilitySli other) {
        Objects.requireNonNull(other, "other 不能为 null");
        if (serviceCategory != other.serviceCategory) {
            throw new IllegalArgumentException("服务类别不一致，无法合并: "
                    + serviceCategory + " vs " + other.serviceCategory);
        }
        Instant start = periodStart.isBefore(other.periodStart) ? periodStart : other.periodStart;
        Instant end = periodEnd.isAfter(other.periodEnd) ? periodEnd : other.periodEnd;
        long total = totalCount + other.totalCount;
        long qualified = qualifiedCount + other.qualifiedCount;
        long available = availableCount + other.availableCount;
        long excluded = excludedCount + other.excludedCount;
        long failed = failedCount + other.failedCount;
        long timeout = timeoutCount + other.timeoutCount;
        Duration p50 = mergeLatency(p50Latency, other.p50Latency, qualified, other.qualifiedCount);
        Duration p95 = mergeLatency(p95Latency, other.p95Latency, qualified, other.qualifiedCount);
        Duration p99 = mergeLatency(p99Latency, other.p99Latency, qualified, other.qualifiedCount);
        return new AvailabilitySli(serviceCategory, start, end,
                total, qualified, available, excluded, failed, timeout,
                p50, p95, p99, Instant.now());
    }

    private static Duration mergeLatency(Duration a, Duration b, long weightA, long weightB) {
        if (a == null) return b;
        if (b == null) return a;
        long totalWeight = weightA + weightB;
        if (totalWeight == 0) return a;
        double weightedA = a.toMillis() * weightA;
        double weightedB = b.toMillis() * weightB;
        return Duration.ofMillis((long) ((weightedA + weightedB) / totalWeight));
    }
}
