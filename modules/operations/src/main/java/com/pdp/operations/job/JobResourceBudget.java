package com.pdp.operations.job;

import java.time.Duration;
import java.util.Objects;

/**
 * 后台作业资源预算值对象。
 *
 * <p>对应 spec.md 状态机表"后台作业"前置条件"资源预算有效"和 research.md 第 5 节连接池策略：
 * "所有应用副本、Flowable 异步执行器、后台执行器和临时迁移池的连接上限之和不超过数据库可用连接的 70%"。
 *
 * <p>资源预算在作业提交时声明，{@link BackgroundJobCoordinator} 在调度前校验：
 * <ul>
 *   <li>超时：作业运行超过 {@code maxDuration} 自动暂停（保留检查点）；</li>
 *   <li>数据库连接：同时运行的作业数据库连接总和不超过 {@code maxConcurrentDbConnections}；</li>
 *   <li>失败阈值：失败条目数或失败率超阈值自动暂停或失败；</li>
 *   <li>节流策略：达到在线池等待或数据库负载阈值时自动限流或暂停作业（research.md）。</li>
 * </ul>
 *
 * @param maxDuration             最大运行时长（超时自动暂停）
 * @param maxConcurrentDbConnections 作业占用的最大数据库连接数
 * @param maxFailureCount         最大失败条目数（超过自动转为 FAILED）
 * @param maxFailureRate          最大失败率 [0.0, 1.0]（超过自动暂停）
 * @param throttlePolicy          节流策略（{@link ThrottlePolicy}）
 */
public record JobResourceBudget(
        Duration maxDuration,
        int maxConcurrentDbConnections,
        int maxFailureCount,
        double maxFailureRate,
        ThrottlePolicy throttlePolicy) {

    public JobResourceBudget {
        Objects.requireNonNull(maxDuration, "maxDuration 不能为 null");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration 必须为正: " + maxDuration);
        }
        if (maxConcurrentDbConnections < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrentDbConnections 必须 >= 1: " + maxConcurrentDbConnections);
        }
        if (maxFailureCount < 0) {
            throw new IllegalArgumentException("maxFailureCount 不能为负: " + maxFailureCount);
        }
        if (maxFailureRate < 0.0 || maxFailureRate > 1.0) {
            throw new IllegalArgumentException("maxFailureRate 必须在 [0.0, 1.0]: " + maxFailureRate);
        }
        Objects.requireNonNull(throttlePolicy, "throttlePolicy 不能为 null");
    }

    /**
     * 默认资源预算（适用于大多数 P1 作业）。
     * <p>最大运行 1 小时、2 个数据库连接、100 次失败、10% 失败率、自适应节流。
     */
    public static JobResourceBudget defaultBudget() {
        return new JobResourceBudget(
                Duration.ofHours(1), 2, 100, 0.10, ThrottlePolicy.ADAPTIVE);
    }

    /**
     * 迁移作业资源预算（放宽时长和失败数）。
     */
    public static JobResourceBudget migrationBudget() {
        return new JobResourceBudget(
                Duration.ofHours(6), 4, 1000, 0.05, ThrottlePolicy.CONSERVATIVE);
    }

    /**
     * 投影重建作业资源预算。
     */
    public static JobResourceBudget projectionRebuildBudget() {
        return new JobResourceBudget(
                Duration.ofHours(2), 2, 50, 0.05, ThrottlePolicy.ADAPTIVE);
    }

    /**
     * 校验当前失败数是否超阈值。
     */
    public boolean isFailureCountExceeded(int currentFailureCount) {
        return maxFailureCount > 0 && currentFailureCount >= maxFailureCount;
    }

    /**
     * 校验当前失败率是否超阈值。
     */
    public boolean isFailureRateExceeded(int processedItems, int failureCount) {
        if (processedItems <= 0) {
            return false;
        }
        double rate = (double) failureCount / processedItems;
        return rate > maxFailureRate;
    }

    /**
     * 节流策略。
     */
    public enum ThrottlePolicy {
        /** 激进：高吞吐，失败率阈值宽松，适用于离线大批量。 */
        AGGRESSIVE,
        /** 自适应：根据数据库负载动态调整并发和批次大小（默认）。 */
        ADAPTIVE,
        /** 保守：低吞吐，优先保护在线连接（迁移作业默认）。 */
        CONSERVATIVE,
        /** 不节流：仅当作业无数据库压力时使用（如纯内存统计）。 */
        NONE
    }
}
