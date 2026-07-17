package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间角色（FR-063）。
 *
 * <p>聚合功能权限键集合（{@code <domain>.<resource>.<action>}）与数据范围类型，
 * 绑定到成员后形成权限决策依据。系统角色（{@code isSystem=true}）不可删除。
 */
public record WorkspaceRole(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        List<String> permissions,
        DataScopeType dataScopeType,
        RoleStatus status,
        boolean isSystem,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public WorkspaceRole {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (dataScopeType == null) {
            throw new IllegalArgumentException("dataScopeType 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    public boolean canDisable() {
        return status == RoleStatus.ACTIVE && !isSystem;
    }
}
