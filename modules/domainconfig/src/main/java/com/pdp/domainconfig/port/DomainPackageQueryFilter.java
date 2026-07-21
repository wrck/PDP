package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageLayer;
import com.pdp.domainconfig.domain.packageversion.DomainPackageStatus;

import java.util.UUID;

/**
 * 领域包分页查询过滤条件。
 *
 * <p>对应 OpenAPI {@code GET /domain-packages} 的可选查询参数。
 * 所有字段可选；为 null 表示不按该字段过滤。
 *
 * @param workspaceId 工作空间归属过滤；PLATFORM_STANDARD 层级可跨工作空间共享，传 null 时返回所有
 * @param layer 层级过滤
 * @param status 状态过滤
 * @param parentPackageId 父包过滤；用于查询某父包下的子包
 * @param stableKeyLike 稳定键模糊匹配
 * @param nameLike 名称模糊匹配
 */
public record DomainPackageQueryFilter(
        UUID workspaceId,
        DomainPackageLayer layer,
        DomainPackageStatus status,
        UUID parentPackageId,
        String stableKeyLike,
        String nameLike) {

    public static DomainPackageQueryFilter empty() {
        return new DomainPackageQueryFilter(null, null, null, null, null, null);
    }

    public static DomainPackageQueryFilter byWorkspace(UUID workspaceId) {
        return new DomainPackageQueryFilter(workspaceId, null, null, null, null, null);
    }
}
