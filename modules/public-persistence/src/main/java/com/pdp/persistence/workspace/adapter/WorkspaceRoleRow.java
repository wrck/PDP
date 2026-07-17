package com.pdp.persistence.workspace.adapter;

import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.RoleStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 工作空间角色持久化行。
 *
 * <p>权限键集合以 JSON 文本列存储为 {@code permissionsJson}，由适配器在装配
 * {@link com.pdp.workspace.domain.WorkspaceRole} 时反序列化为 {@code List<String>}。
 */
public record WorkspaceRoleRow(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        String permissionsJson,
        DataScopeType dataScopeType,
        RoleStatus status,
        boolean isSystem,
        int revision,
        Instant createdAt,
        Instant updatedAt) {
}
