package com.pdp.persistence.workspace.adapter;

import com.pdp.workspace.domain.DataScopeType;

import java.time.Instant;
import java.util.UUID;

/**
 * 数据范围持久化行。
 *
 * <p>规则集合以 JSON 文本列存储为 {@code rulesJson}，由适配器在装配
 * {@link com.pdp.workspace.domain.DataScope} 时反序列化为 {@code List<DataScopeRule>}。
 */
public record DataScopeRow(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        DataScopeType scopeType,
        String rulesJson,
        int revision,
        Instant createdAt,
        Instant updatedAt) {
}
