package com.pdp.workspace.domain;

/**
 * 数据范围规则（FR-063）。
 *
 * <p>单条过滤规则：字段 {@code field} 经 {@code operator} 与 {@code value} 比较限定可见行。
 * {@code value} 类型依字段而定，可为字符串、数值、布尔、数组或 null（配合 IS_NULL/NOT_NULL）。
 * 持久化时由适配器序列化为 JSON 文档。
 */
public record DataScopeRule(String field, String operator, Object value) {

    public DataScopeRule {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field 不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空");
        }
    }
}
