package com.pdp.shared.operation;

import java.util.Objects;

/**
 * 单条影响条目（FR-168 影响预览的结构化项）。
 *
 * <p>每条记录一类影响，如"受影响交付件 N 个"、"未完成审批 M 条"、"不可逆变更：原基线将被覆盖"等。
 * 操作者基于条目列表决定是否确认操作。
 *
 * @param category             影响类别（如 AFFECTED_DELIVERABLE、PENDING_APPROVAL、IRREVERSIBLE_OVERWRITE）
 * @param description          人类可读描述
 * @param affectedObjectCount  受影响对象数量（0 表示该类别无对象，但仍提示风险）
 * @param severity             严重度（INFO/WARNING/IRREVERSIBLE）
 * @param irreversible         是否不可逆（true 时操作者 MUST 显式确认）
 * @param detailJson           结构化详情 JSON（如对象 ID 列表、字段差异），可选
 */
public record ImpactItem(
        String category,
        String description,
        int affectedObjectCount,
        ImpactSeverity severity,
        boolean irreversible,
        String detailJson) {

    public ImpactItem {
        Objects.requireNonNull(category, "category 不能为空");
        if (category.isBlank()) {
            throw new IllegalArgumentException("category 不能为空白");
        }
        Objects.requireNonNull(description, "description 不能为空");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description 不能为空白");
        }
        if (affectedObjectCount < 0) {
            throw new IllegalArgumentException("affectedObjectCount 不能为负");
        }
        Objects.requireNonNull(severity, "severity 不能为空");
        // irreversible=true 时 severity MUST 为 IRREVERSIBLE
        if (irreversible && severity != ImpactSeverity.IRREVERSIBLE) {
            throw new IllegalArgumentException(
                    "irreversible=true 时 severity 必须为 IRREVERSIBLE，当前: " + severity);
        }
    }

    /**
     * 创建可逆影响条目（severity 由 affectedObjectCount 推断：0=INFO，>0=WARNING）。
     */
    public static ImpactItem reversible(String category, String description,
                                         int affectedObjectCount, String detailJson) {
        ImpactSeverity severity = affectedObjectCount == 0
                ? ImpactSeverity.INFO : ImpactSeverity.WARNING;
        return new ImpactItem(category, description, affectedObjectCount, severity, false, detailJson);
    }

    /**
     * 创建不可逆影响条目（severity 强制 IRREVERSIBLE）。
     */
    public static ImpactItem irreversible(String category, String description,
                                           int affectedObjectCount, String detailJson) {
        return new ImpactItem(category, description, affectedObjectCount,
                ImpactSeverity.IRREVERSIBLE, true, detailJson);
    }
}
