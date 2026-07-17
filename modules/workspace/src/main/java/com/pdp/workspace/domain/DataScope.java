package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 数据范围定义（FR-063）。
 *
 * <p>命名可复用的过滤规则集合，按 {@link DataScopeType} 分类，可绑定到成员以限定可见数据。
 * 与功能权限正交：权限决定能否操作，数据范围决定操作哪些数据。
 */
public record DataScope(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        DataScopeType scopeType,
        List<DataScopeRule> rules,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public DataScope {
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
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType 不能为 null");
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
