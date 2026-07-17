package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 组织（FR-004）。
 *
 * <p>工作空间内的组织树节点，通过物化路径 {@code path}（如 {@code /ROOT/REGION-A/ORG-X}）
 * 与 {@code depth} 支持高效层级查询。{@code parentId} 为 null 表示顶层组织。
 * 停用为软删除，保留子组织与历史关联。
 */
public record Organization(
        UUID id,
        UUID workspaceId,
        String code,
        String name,
        String description,
        UUID parentId,
        String path,
        int depth,
        OrganizationStatus status,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public Organization {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth 不能为负数");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }
}
