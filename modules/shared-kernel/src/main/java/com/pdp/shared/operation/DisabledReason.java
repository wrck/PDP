package com.pdp.shared.operation;

import java.util.Objects;

/**
 * 操作禁用原因（FR-168、spec.md 末段）。
 *
 * <p>对应 spec.md："P1 的高风险操作目录必须预注册'认证数据库切换'类型及其影响摘要、权限和
 * 禁用原因契约，但只有 P2 提供经认证适配器和迁移执行器支撑的实际操作。"
 *
 * <p>当调用方尝试对 P1 禁用的操作类型（如 {@link HighRiskOperationType#DATABASE_SWITCH}）
 * 调用 {@link HighRiskOperationPort#preview} 或 {@link HighRiskOperationPort#execute} 时，
 * 端口实现 MUST 返回包含稳定禁用原因的结果，而非抛出异常——这保证前端能展示稳定的禁用提示。
 *
 * <p><strong>稳定键契约</strong>：{@link #stableKey()} 在 P1/P2 间保持稳定，P2 启用操作后
 * 不再返回此原因；P1 期间禁止修改键值，避免前端硬编码失效。
 */
public record DisabledReason(
        String stableKey,
        String summary,
        String targetPhase) {

    public DisabledReason {
        Objects.requireNonNull(stableKey, "stableKey 不能为空");
        if (stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空白");
        }
        Objects.requireNonNull(summary, "summary 不能为空");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary 不能为空白");
        }
        Objects.requireNonNull(targetPhase, "targetPhase 不能为空");
        if (targetPhase.isBlank()) {
            throw new IllegalArgumentException("targetPhase 不能为空白");
        }
    }

    /**
     * P1 认证数据库切换禁用原因。
     *
     * <p>稳定键 {@code DATABASE_SWITCH.P1_DISABLED}，P2 启用后不再返回。
     */
    public static DisabledReason databaseSwitchP1Disabled() {
        return new DisabledReason(
                "DATABASE_SWITCH.P1_DISABLED",
                "认证数据库切换在 P1 仅预注册类型与扩展契约，不提供可执行入口；"
                        + "P2 提供经认证适配器和迁移执行器支撑的实际操作",
                "P2");
    }

    /**
     * 通用 P1 禁用原因构造（用于其他未来预注册类型）。
     *
     * @param operationTypeStableKey 操作类型稳定键
     * @param summary                禁用摘要
     * @param targetPhase            目标阶段（如 P2、P3）
     * @return 禁用原因
     */
    public static DisabledReason p1Disabled(
            String operationTypeStableKey, String summary, String targetPhase) {
        return new DisabledReason(
                operationTypeStableKey + ".P1_DISABLED", summary, targetPhase);
    }
}
