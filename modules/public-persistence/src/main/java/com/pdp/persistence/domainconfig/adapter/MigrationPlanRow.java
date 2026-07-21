package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageMigrationPlan;
import com.pdp.domainconfig.domain.packageversion.MigrationFailureIsolation;
import com.pdp.domainconfig.domain.packageversion.MigrationPlanStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 迁移计划持久化行（{@code domain_package_migration_plan}）。
 *
 * <p>对应 FR-168 高风险操作框架。{@code batchStrategyJson} 直接存储 JSON 字符串。
 */
public record MigrationPlanRow(
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

    /** 从行还原 {@link DomainPackageMigrationPlan}。 */
    public DomainPackageMigrationPlan toPlan() {
        return new DomainPackageMigrationPlan(
                id, packageId, fromVersionId, toVersionId, status,
                batchStrategyJson, failureIsolation,
                totalBatches, completedBatches, failedInstances,
                impactPreviewId, jobId, rollbackWindowExpiresAt, reason,
                revision, createdAt, updatedAt);
    }

    /** 从 {@link DomainPackageMigrationPlan} 拆解为行。 */
    public static MigrationPlanRow fromPlan(DomainPackageMigrationPlan plan) {
        return new MigrationPlanRow(
                plan.id(),
                plan.packageId(),
                plan.fromVersionId(),
                plan.toVersionId(),
                plan.status(),
                plan.batchStrategyJson(),
                plan.failureIsolation(),
                plan.totalBatches(),
                plan.completedBatches(),
                plan.failedInstances(),
                plan.impactPreviewId(),
                plan.jobId(),
                plan.rollbackWindowExpiresAt(),
                plan.reason(),
                plan.revision(),
                plan.createdAt(),
                plan.updatedAt());
    }
}
