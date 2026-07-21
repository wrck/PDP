package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.MigrationPlanStatus;

import java.util.UUID;

/**
 * 领域包迁移计划分页查询过滤条件。
 *
 * <p>对应 OpenAPI {@code GET /domain-packages/{packageId}/migration-plans} 的可选查询参数。
 *
 * @param packageId 所属领域包 ID；null 表示跨包查询
 * @param status 状态过滤；null 表示不过滤
 * @param fromVersionId 源版本过滤
 * @param toVersionId 目标版本过滤
 */
public record MigrationPlanQueryFilter(
        UUID packageId,
        MigrationPlanStatus status,
        UUID fromVersionId,
        UUID toVersionId) {

    public static MigrationPlanQueryFilter byPackage(UUID packageId) {
        return new MigrationPlanQueryFilter(packageId, null, null, null);
    }

    public static MigrationPlanQueryFilter activeByPackage(UUID packageId) {
        return new MigrationPlanQueryFilter(
                packageId,
                null,
                null,
                null);
    }
}
