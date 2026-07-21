package com.pdp.domainconfig.domain.metamodel;

import java.util.List;

/**
 * 动态对象定义（domain-package.schema.json objectDefinition）。
 *
 * <p>FR-008 领域包 MUST 能扩展平台允许扩展的核心对象并创建全新业务对象。
 * FR-010 领域包不得覆盖核心对象身份、工作空间归属、基础权限、审计、版本和平台保留动作。
 *
 * <p>{@link #kind} 为 {@link ObjectKind#CORE_EXTENSION} 时必须指定 {@link #coreObjectType}。
 * {@code states} 与 {@code transitions} 由 T117 行为模型承载，但作为对象定义的引用根。
 */
public record ObjectDefinition(
        String stableKey,
        ObjectKind kind,
        String coreObjectType,
        LocalizedText label,
        String titleFieldKey,
        List<FieldDefinition> fields,
        List<RelationDefinition> relations) {

    public ObjectDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为 null");
        }
        if (label == null) {
            throw new IllegalArgumentException("label 不能为 null");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields 不能为空");
        }
        if (kind == ObjectKind.CORE_EXTENSION && (coreObjectType == null || coreObjectType.isBlank())) {
            throw new IllegalArgumentException("CORE_EXTENSION 必须指定 coreObjectType");
        }
        fields = List.copyOf(fields);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** 查找指定 stableKey 的字段定义。 */
    public FieldDefinition field(String fieldKey) {
        return fields.stream()
                .filter(f -> f.stableKey().equals(fieldKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("字段不存在：" + fieldKey));
    }

    /** 判断是否包含指定 stableKey 的字段。 */
    public boolean hasField(String fieldKey) {
        return fields.stream().anyMatch(f -> f.stableKey().equals(fieldKey));
    }
}
