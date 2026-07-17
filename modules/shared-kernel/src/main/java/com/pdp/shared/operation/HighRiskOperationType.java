package com.pdp.shared.operation;

/**
 * 高风险操作类型（FR-168、SC-039）。
 *
 * <p>对应 spec.md "高风险操作目录" 的 6 类操作。每类操作 MUST 提供影响预览、明确确认、审计
 * 以及撤销、回退或人工补偿路径。
 *
 * <p><strong>P1 启用状态</strong>：
 * <ul>
 *   <li>{@link #DOMAIN_PACKAGE_PUBLISH}、{@link #PROJECT_ROLLBACK}、{@link #BASELINE_REPLACE}、
 *       {@link #DELIVERABLE_RELEASE}、{@link #DATA_DISPOSAL}、{@link #HISTORY_MIGRATION}：
 *       P1 由对应业务能力提供可执行入口（领域包、项目、基线、交付件、数据导出/处置、历史迁移）。</li>
 *   <li>{@link #DATABASE_SWITCH}：P1 仅预注册类型、影响摘要、权限和禁用原因契约，
 *       <strong>不</strong>提供可执行切换入口；P2 提供经认证适配器和迁移执行器支撑的实际操作
 *       （见 spec.md 末段：P1 的高风险操作目录必须预注册"认证数据库切换"类型）。</li>
 * </ul>
 *
 * <p>类型扩展契约稳定：新增类型 MUST 通过 {@link HighRiskOperationRegistry#register} 注册，
 * 不得修改已有类型的稳定键（{@link #stableKey()}）。
 */
public enum HighRiskOperationType {

    /** 领域包发布、升级和实例迁移（FR-174）。 */
    DOMAIN_PACKAGE_PUBLISH("DOMAIN_PACKAGE.PUBLISH"),

    /** 项目/阶段受控回退和项目关闭。 */
    PROJECT_ROLLBACK("PROJECT.ROLLBACK"),

    /** 基线替换和人工进度调整。 */
    BASELINE_REPLACE("BASELINE.REPLACE"),

    /** 交付件发布和审批终态。 */
    DELIVERABLE_RELEASE("DELIVERABLE.RELEASE"),

    /** 数据导出、法律保留解除和处置。 */
    DATA_DISPOSAL("DATA.DISPOSAL"),

    /** 历史迁移（含上线切换源系统到 PDP）。 */
    HISTORY_MIGRATION("HISTORY.MIGRATION"),

    /**
     * 认证数据库切换（P2 能力）。
     *
     * <p>P1 仅预注册类型与禁用原因契约，不提供可执行入口；
     * 调用 {@link HighRiskOperationPort#preview} 或 {@link HighRiskOperationPort#execute}
     * 时返回稳定禁用原因 {@code DATABASE_SWITCH.P1_DISABLED}。
     */
    DATABASE_SWITCH("DATABASE.SWITCH");

    private final String stableKey;

    HighRiskOperationType(String stableKey) {
        this.stableKey = stableKey;
    }

    /**
     * 稳定键（持久化、审计、API 与日志使用，禁止依赖枚举序号）。
     *
     * @return 全大写点分稳定键，如 {@code DOMAIN_PACKAGE.PUBLISH}
     */
    public String stableKey() {
        return stableKey;
    }

    /**
     * P1 是否启用可执行入口。
     *
     * <p>{@link #DATABASE_SWITCH} 在 P1 返回 false，调用方必须返回稳定禁用原因；
     * 其余类型在 P1 由对应业务能力提供可执行入口，返回 true。
     *
     * @return true 表示 P1 可执行；false 表示 P1 仅预注册，P2 启用
     */
    public boolean executableInP1() {
        return this != DATABASE_SWITCH;
    }

    /**
     * 按稳定键解析类型。
     *
     * @param stableKey 稳定键（大小写敏感）
     * @return 对应类型
     * @throws IllegalArgumentException 未知稳定键
     */
    public static HighRiskOperationType fromStableKey(String stableKey) {
        if (stableKey == null) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        for (HighRiskOperationType t : values()) {
            if (t.stableKey.equals(stableKey)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知高风险操作类型稳定键: " + stableKey);
    }
}
