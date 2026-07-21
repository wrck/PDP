package com.pdp.domainconfig.domain.metamodel;

import java.util.List;
import java.util.Map;

/**
 * 字段定义（domain-package.schema.json fieldDefinition）。
 *
 * <p>{@code coreFieldKey} 用于声明本字段复用平台核心字段目录中的字段（FR-132、FR-134）；
 * 发布前由 {@code DomainPackageValidationService}（T121）检测重名/语义/标识/数据来源冲突。
 */
public record FieldDefinition(
        String stableKey,
        LocalizedText label,
        DataType dataType,
        boolean required,
        boolean sensitive,
        UniqueScope uniqueScope,
        IndexMode indexMode,
        String coreFieldKey,
        Object defaultValue,
        Map<String, Object> validation,
        List<FieldOption> options) {

    public FieldDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (label == null) {
            throw new IllegalArgumentException("label 不能为 null");
        }
        if (dataType == null) {
            throw new IllegalArgumentException("dataType 不能为 null");
        }
        if (uniqueScope == null) {
            uniqueScope = UniqueScope.NONE;
        }
        if (indexMode == null) {
            indexMode = IndexMode.NONE;
        }
        options = options == null ? List.of() : List.copyOf(options);
        validation = validation == null ? Map.of() : Map.copyOf(validation);
    }

    /** 是否为平台核心字段复用。 */
    public boolean isCoreFieldReuse() {
        return coreFieldKey != null && !coreFieldKey.isBlank();
    }

    public record FieldOption(String key, LocalizedText label) {
        public FieldOption {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("option key 不能为空");
            }
            if (label == null) {
                throw new IllegalArgumentException("option label 不能为 null");
            }
        }
    }
}
