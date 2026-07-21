package com.pdp.domainconfig.domain.metamodel;

import java.util.List;
import java.util.Map;

/**
 * 视图定义（domain-package.schema.json viewDefinition）。
 *
 * <p>视图为同一对象提供不同呈现方式（列表、看板、日历、时间线、详情）。
 * {@code columns} 与 {@code filters} 引用对象字段 stableKey。
 */
public record ViewDefinition(
        String stableKey,
        String objectKey,
        ViewType viewType,
        List<String> columns,
        List<Map<String, Object>> filters) {

    public ViewDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        if (viewType == null) {
            throw new IllegalArgumentException("viewType 不能为 null");
        }
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}
