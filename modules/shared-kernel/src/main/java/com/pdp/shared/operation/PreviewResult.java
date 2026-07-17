package com.pdp.shared.operation;

import java.util.Optional;

/**
 * 影响预览结果（FR-168）。
 *
 * <p>封装 {@link HighRiskOperationPort#preview} 的返回值，区分两种情况：
 * <ul>
 *   <li>操作启用：返回 {@link ImpactPreview} 与 {@link CompensationPlan}；</li>
 *   <li>操作禁用（如 P1 的 {@link HighRiskOperationType#DATABASE_SWITCH}）：返回 {@link DisabledReason}，
 *       不抛异常，保证前端展示稳定禁用提示。</li>
 * </ul>
 */
public record PreviewResult(
        ImpactPreview preview,
        CompensationPlan compensationPlan,
        DisabledReason disabledReason) {

    public PreviewResult {
        // 二者互斥：启用时有 preview+compensationPlan，禁用时有 disabledReason
        if (preview == null && disabledReason == null) {
            throw new IllegalArgumentException("preview 和 disabledReason 不能同时为空");
        }
        if (preview != null && disabledReason != null) {
            throw new IllegalArgumentException("preview 和 disabledReason 不能同时非空");
        }
        if (preview != null && compensationPlan == null) {
            throw new IllegalArgumentException("启用操作时 compensationPlan 不能为空");
        }
        if (disabledReason != null && compensationPlan != null) {
            throw new IllegalArgumentException("禁用操作时 compensationPlan 必须为空");
        }
    }

    /**
     * 创建启用操作的结果。
     */
    public static PreviewResult enabled(ImpactPreview preview, CompensationPlan plan) {
        return new PreviewResult(preview, plan, null);
    }

    /**
     * 创建禁用操作的结果。
     */
    public static PreviewResult disabled(DisabledReason reason) {
        return new PreviewResult(null, null, reason);
    }

    /**
     * 操作是否启用。
     */
    public boolean isEnabled() {
        return preview != null;
    }

    /**
     * 操作是否禁用。
     */
    public boolean isDisabled() {
        return disabledReason != null;
    }

    /**
     * 获取禁用原因（操作禁用时返回 Optional.of(reason)，启用时返回 empty）。
     */
    public Optional<DisabledReason> disabledReason() {
        return Optional.ofNullable(disabledReason);
    }

    /**
     * 获取预览（操作启用时返回 Optional.of(preview)，禁用时返回 empty）。
     */
    public Optional<ImpactPreview> preview() {
        return Optional.ofNullable(preview);
    }

    /**
     * 获取补偿计划（操作启用时返回 Optional.of(plan)，禁用时返回 empty）。
     */
    public Optional<CompensationPlan> compensationPlan() {
        return Optional.ofNullable(compensationPlan);
    }
}
