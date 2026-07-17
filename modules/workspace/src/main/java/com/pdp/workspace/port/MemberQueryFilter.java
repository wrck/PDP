package com.pdp.workspace.port;

import com.pdp.workspace.domain.MemberStatus;

import java.util.UUID;

/**
 * 工作空间成员分页查询过滤条件。
 *
 * <p>对应 OpenAPI {@code GET /workspaces/{id}/members} 的可选查询参数。
 * 所有字段可选；为 null 表示不按该字段过滤。
 *
 * @param organizationId 组织归属过滤
 * @param roleId 角色过滤（命中成员-角色关联表）
 * @param status 成员状态过滤
 */
public record MemberQueryFilter(
        UUID organizationId,
        UUID roleId,
        MemberStatus status) {

    public static MemberQueryFilter empty() {
        return new MemberQueryFilter(null, null, null);
    }

    public static MemberQueryFilter of(UUID organizationId, UUID roleId, MemberStatus status) {
        return new MemberQueryFilter(organizationId, roleId, status);
    }
}
