package com.pdp.domainconfig.domain.metamodel;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 平台统一核心字段目录条目（FR-132、FR-173、SC-025）。
 *
 * <p>核心字段由平台维护统一规范中文名、英文稳定键、唯一业务定义、标准来源、允许别名与
 * 禁止混用项。领域包扩展字段必须通过 {@code CoreFieldReuseDeclaration} 声明与核心字段的
 * 复用或差异关系；SC-025 已发布领域包中与核心字段重复或语义冲突的扩展字段数量为 0。
 *
 * <p>{@code allowedOverride} 标记字段是否允许领域包通过声明式 override 修改其展示等次要
 * 属性；语义、唯一性、归属关系或平台级统计口径不得被覆盖。
 */
public record CoreFieldCatalogEntry(
        UUID id,
        String stableKey,
        String coreObjectType,
        String label,
        DataType dataType,
        String semantics,
        boolean allowedOverride,
        CoreFieldSource source,
        Set<String> aliases,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public CoreFieldCatalogEntry {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (coreObjectType == null || coreObjectType.isBlank()) {
            throw new IllegalArgumentException("coreObjectType 不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        if (dataType == null) {
            throw new IllegalArgumentException("dataType 不能为 null");
        }
        if (semantics == null || semantics.isBlank()) {
            throw new IllegalArgumentException("semantics 不能为空");
        }
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    /** 判断领域包扩展字段 stableKey 是否与该核心字段冲突（含别名）。 */
    public boolean conflictsWith(String extensionFieldKey) {
        if (extensionFieldKey == null) {
            return false;
        }
        return stableKey.equalsIgnoreCase(extensionFieldKey)
                || aliases.stream().anyMatch(a -> a.equalsIgnoreCase(extensionFieldKey));
    }
}
