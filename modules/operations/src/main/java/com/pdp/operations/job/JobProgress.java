package com.pdp.operations.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 后台作业进度快照值对象。
 *
 * <p>对应 OpenAPI {@code BackgroundJob.progress} 字段，并扩展吞吐量和 ETA 等运维监控指标。
 * 由 {@link BackgroundJobCoordinator} 在作业运行时定期持久化，供 {@link BackgroundJobPort#getJob} 查询。
 *
 * <p>进度 MUST 基于检查点的 {@link JobCheckpoint#processedItems()} 和 {@link JobCheckpoint#totalItems()}，
 * 不依赖外部计数器（保证与检查点一致，恢复后准确）。
 *
 * @param progressPercent 进度百分比 [0, 100]
 * @param processedItems  已处理条目数
 * @param totalItems      总条目数（未知为 -1）
 * @param failureCount    失败条目数
 * @param throughputItemsPerSecond 吞吐量（条/秒，未知为 -1）
 * @param estimatedRemaining 估算剩余时间（未知为 null）
 * @param snapshotAt      快照时间
 */
public record JobProgress(
        int progressPercent,
        int processedItems,
        int totalItems,
        int failureCount,
        double throughputItemsPerSecond,
        Duration estimatedRemaining,
        Instant snapshotAt) {

    public JobProgress {
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent 必须在 [0, 100]: " + progressPercent);
        }
        if (processedItems < 0) {
            throw new IllegalArgumentException("processedItems 不能为负: " + processedItems);
        }
        if (totalItems < -1) {
            throw new IllegalArgumentException("totalItems 不能小于 -1: " + totalItems);
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount 不能为负: " + failureCount);
        }
        Objects.requireNonNull(snapshotAt, "snapshotAt 不能为 null");
    }

    /**
     * 基于检查点构造进度快照。
     *
     * @param checkpoint    当前检查点
     * @param failureCount  失败条目数
     * @param startedAt     作业启动时间
     * @param snapshotAt    快照时间
     * @return 进度快照
     */
    public static JobProgress fromCheckpoint(
            JobCheckpoint checkpoint, int failureCount, Instant startedAt, Instant snapshotAt) {
        Objects.requireNonNull(checkpoint, "checkpoint 不能为 null");
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
        int percent = checkpoint.progressPercent();
        int processed = checkpoint.processedItems();
        int total = checkpoint.totalItems();

        double throughput = -1.0;
        Duration remaining = null;
        Duration elapsed = Duration.between(startedAt, snapshotAt);
        if (!elapsed.isZero() && !elapsed.isNegative() && processed > 0) {
            throughput = processed / (elapsed.toMillis() / 1000.0);
            if (total > processed && throughput > 0) {
                long remainingItems = (long) total - processed;
                long remainingSeconds = (long) (remainingItems / throughput);
                remaining = Duration.ofSeconds(remainingSeconds);
            }
        }
        return new JobProgress(percent, processed, total, failureCount, throughput, remaining, snapshotAt);
    }

    /** 是否有失败条目。 */
    public boolean hasFailures() {
        return failureCount > 0;
    }

    /** 失败率 [0.0, 1.0]，totalItems 未知返回 0。 */
    public double failureRate() {
        if (totalItems <= 0) {
            return 0.0;
        }
        return (double) failureCount / totalItems;
    }
}
