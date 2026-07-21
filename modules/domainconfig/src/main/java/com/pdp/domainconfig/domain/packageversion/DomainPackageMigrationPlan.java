package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包迁移计划（FR-168 高风险操作框架）。
 *
 * <p>记录从 {@code fromVersionId} 升级到 {@code toVersionId} 的分批迁移执行计划。
 * {@link #batchStrategyJson} 描述分批策略（按对象、按工作空间、按时间窗等）；
 * {@link #failureIsolation} 决定失败时的处置策略；
 * {@link #rollbackWindowExpiresAt} 为可回滚时间窗截止，超过后只能前向修复。
 */
public record DomainPackageMigrationPlan(
        UUID id,
        UUID packageId,
        UUID fromVersionId,
        UUID toVersionId,
        MigrationPlanStatus status,
        String batchStrategyJson,
        MigrationFailureIsolation failureIsolation,
        int totalBatches,
        int completedBatches,
        int failedInstances,
        UUID impactPreviewId,
        UUID jobId,
        Instant rollbackWindowExpiresAt,
        String reason,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public DomainPackageMigrationPlan {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (packageId == null) {
            throw new IllegalArgumentException("packageId 不能为 null");
        }
        if (fromVersionId == null) {
            throw new IllegalArgumentException("fromVersionId 不能为 null");
        }
        if (toVersionId == null) {
            throw new IllegalArgumentException("toVersionId 不能为 null");
        }
        if (fromVersionId.equals(toVersionId)) {
            throw new IllegalArgumentException("fromVersionId 与 toVersionId 不能相同");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        if (batchStrategyJson == null || batchStrategyJson.isBlank()) {
            throw new IllegalArgumentException("batchStrategyJson 不能为空");
        }
        if (failureIsolation == null) {
            throw new IllegalArgumentException("failureIsolation 不能为 null");
        }
        if (totalBatches < 0 || completedBatches < 0 || failedInstances < 0) {
            throw new IllegalArgumentException("批次与失败计数不能为负");
        }
        if (completedBatches > totalBatches) {
            throw new IllegalArgumentException("completedBatches 不能超过 totalBatches");
        }
    }

    /** 是否处于可执行状态（READY）。 */
    public boolean isReady() {
        return status == MigrationPlanStatus.READY;
    }

    /** 是否处于运行中或暂停状态（可继续推进或回滚）。 */
    public boolean isActive() {
        return status == MigrationPlanStatus.RUNNING || status == MigrationPlanStatus.PAUSED;
    }

    /** 是否可回滚（COMPLETED/FAILED 且在回滚时间窗内）。 */
    public boolean canRollback(Instant now) {
        if (status != MigrationPlanStatus.COMPLETED && status != MigrationPlanStatus.FAILED) {
            return false;
        }
        return rollbackWindowExpiresAt == null || !now.isAfter(rollbackWindowExpiresAt);
    }

    /** 进度百分比（0-100）。 */
    public int progressPercent() {
        if (totalBatches == 0) {
            return 0;
        }
        return (int) (100L * completedBatches / totalBatches);
    }
}
