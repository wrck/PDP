package com.pdp.shared.operation;

/**
 * 高风险操作状态机（FR-167、FR-168）。
 *
 * <p>状态迁移：
 * <pre>
 *   DRAFT ──preview──▶ PREVIEWED ──confirm──▶ CONFIRMED ──execute──▶ EXECUTING
 *                                                                  │
 *                                                                  ├──▶ COMPLETED
 *                                                                  ├──▶ COMPENSATED（执行补偿）
 *                                                                  └──▶ FAILED（不可恢复，需人工介入）
 *
 *   任意状态 ──cancel──▶ CANCELLED（仅未达不可逆点前允许）
 * </pre>
 *
 * <p><strong>不可逆点约束</strong>：一旦操作进入 {@link #EXECUTING} 后到达 {@code PointOfNoReturn}，
 * 不允许 {@link #CANCELLED}；只能继续执行或触发补偿路径（{@link #COMPENSATED}）。
 */
public enum OperationState {

    /** 草稿：操作已创建但未生成影响预览。 */
    DRAFT,

    /** 已预览：影响预览已生成，等待操作者确认。预览有有效期，过期需重新生成。 */
    PREVIEWED,

    /** 已确认：操作者基于预览版本明确确认，可执行。 */
    CONFIRMED,

    /** 执行中：操作正在执行，可能已过不可逆点。 */
    EXECUTING,

    /** 已完成：操作成功完成，写入审计与最终状态。 */
    COMPLETED,

    /**
     * 已补偿：操作执行失败或主动触发补偿，按补偿计划完成回退/反向同步/人工补偿。
     * 补偿完成不等于恢复原状（不可逆点后只能前向修复或反向同步）。
     */
    COMPENSATED,

    /**
     * 失败：操作或补偿均无法继续，需人工介入。审计 MUST 记录失败原因和已执行步骤。
     */
    FAILED,

    /**
     * 已取消：操作在到达不可逆点前被取消，无副作用。不可逆点后不允许取消。
     */
    CANCELLED;

    /**
     * 是否终态。
     *
     * @return true 表示操作已结束（COMPLETED/COMPENSATED/FAILED/CANCELLED），不可再迁移
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED || this == CANCELLED;
    }

    /**
     * 是否允许取消（仅未达不可逆点前）。
     *
     * @return true 表示可取消（DRAFT/PREVIEWED/CONFIRMED）
     */
    public boolean isCancellable() {
        return this == DRAFT || this == PREVIEWED || this == CONFIRMED;
    }

    /**
     * 是否允许执行（仅 CONFIRMED 可执行）。
     */
    public boolean isExecutable() {
        return this == CONFIRMED;
    }
}
