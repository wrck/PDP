package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;

import java.util.UUID;

/**
 * 领域包版本分页查询过滤条件。
 *
 * <p>对应 OpenAPI {@code GET /domain-packages/{packageId}/versions} 的可选查询参数。
 *
 * @param packageId 所属领域包 ID（必填）
 * @param status 状态过滤；null 表示不过滤
 * @param semanticVersionLike 语义化版本模糊匹配
 */
public record DomainPackageVersionQueryFilter(
        UUID packageId,
        DomainPackageVersionStatus status,
        String semanticVersionLike) {

    public static DomainPackageVersionQueryFilter byPackage(UUID packageId) {
        return new DomainPackageVersionQueryFilter(packageId, null, null);
    }

    public static DomainPackageVersionQueryFilter byPackageAndStatus(UUID packageId,
                                                                     DomainPackageVersionStatus status) {
        return new DomainPackageVersionQueryFilter(packageId, status, null);
    }
}
