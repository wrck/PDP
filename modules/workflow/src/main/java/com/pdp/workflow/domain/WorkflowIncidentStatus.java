package com.pdp.workflow.domain;

/**
 * 工作流异常记录状态机枚举（FR-174、ADR-0005 第 6 节）。
 *
 * <p>状态迁移路径（data-model.md 第 7.4 节）：
 * <pre>
 *   OPEN → RETRYING → RESOLVED
 *              ↓
 *       MANUAL_ACTION → RESOLVED
 *              ↓
 *         DEAD_LETTER
 * </pre>
 *
 * <p><strong>状态语义</strong>：
 * <ul>
 *   <li>{@link #OPEN}：异常已登记，尚未触发自动重试；</li>
 *   <li>{@link #RETRYING}：引擎正在按退避策略重试（attempts 递增）；</li>
 *   <li>{@link #RESOLVED}：已解决（自动重试成功或人工确认），终态；</li>
 *   <li>{@link #MANUAL_ACTION}：自动重试耗尽，等待人工处理（重试、迁移或补偿）；</li>
 *   <li>{@link #DEAD_LETTER}：死信，人工干预后仍无法恢复，终态需归档。</li>
 * </ul>
 *
 * <p>本枚举为持久化层状态机，不直接暴露于 {@code WorkflowAdministrationPort} 的
 * 诊断读模型（{@link com.pdp.workflow.model.WorkflowIncident}）；诊断读模型通过
 * {@code resolvedAt} 推断解决态。
 */
public enum WorkflowIncidentStatus {
    OPEN,
    RETRYING,
    RESOLVED,
    MANUAL_ACTION,
    DEAD_LETTER;

    /** 稳定键（持久化使用，禁止依赖枚举序号）。 */
    public String stableKey() {
        return name();
    }

    /** 是否为终态。 */
    public boolean isTerminal() {
        return this == RESOLVED || this == DEAD_LETTER;
    }

    /** 是否未解决（用于 countUnresolvedByInstance）。 */
    public boolean isUnresolved() {
        return this != RESOLVED && this != DEAD_LETTER;
    }

    /** 是否允许迁移到目标状态。 */
    public boolean canTransitionTo(WorkflowIncidentStatus target) {
        return switch (this) {
            case OPEN -> target == RETRYING || target == MANUAL_ACTION || target == RESOLVED;
            case RETRYING -> target == RESOLVED || target == MANUAL_ACTION || target == DEAD_LETTER;
            case MANUAL_ACTION -> target == RESOLVED || target == RETRYING || target == DEAD_LETTER;
            case RESOLVED, DEAD_LETTER -> false; // 终态不可迁移
        };
    }

    public static WorkflowIncidentStatus fromStableKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("状态键不能为空");
        }
        return WorkflowIncidentStatus.valueOf(key);
    }
}
