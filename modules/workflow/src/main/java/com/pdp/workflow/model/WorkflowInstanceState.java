package com.pdp.workflow.model;

/**
 * 流程实例状态机枚举（FR-174）。
 *
 * <p>状态迁移路径：
 * <pre>
 *   STARTING → ACTIVE ⇄ SUSPENDED → COMPLETED
 *                  ↓
 *               INCIDENT →（重试/迁移）→ ACTIVE
 *                  ↓
 *               TERMINATED
 * </pre>
 *
 * <p><strong>状态语义</strong>：
 * <ul>
 *   <li>{@link #STARTING}：实例已创建但尚未进入第一个活动（编排消费者启动中）；</li>
 *   <li>{@link #ACTIVE}：正常运行中；</li>
 *   <li>{@link #SUSPENDED}：已暂停（管理动作或人工干预），可恢复；</li>
 *   <li>{@link #COMPLETED}：正常完成（终态）；</li>
 *   <li>{@link #TERMINATED}：异常终止（终态，不可恢复）；</li>
 *   <li>{@link #INCIDENT}：引擎检测到事件（如异步作业失败、死信），需人工处理。</li>
 * </ul>
 */
public enum WorkflowInstanceState {
    STARTING,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    TERMINATED,
    INCIDENT;

    /** 稳定键（持久化使用，禁止依赖枚号序）。 */
    public String stableKey() {
        return name();
    }

    /** 是否为终态。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == TERMINATED;
    }

    /** 是否可执行管理动作（暂停/恢复/终止）。 */
    public boolean isManageable() {
        return this != TERMINATED;
    }

    /** 是否允许迁移到目标状态。 */
    public boolean canTransitionTo(WorkflowInstanceState target) {
        return switch (this) {
            case STARTING -> target == ACTIVE || target == TERMINATED || target == INCIDENT;
            case ACTIVE -> target == SUSPENDED || target == COMPLETED
                    || target == TERMINATED || target == INCIDENT;
            case SUSPENDED -> target == ACTIVE || target == TERMINATED;
            case INCIDENT -> target == ACTIVE || target == SUSPENDED || target == TERMINATED;
            case COMPLETED, TERMINATED -> false; // 终态不可迁移
        };
    }

    public static WorkflowInstanceState fromStableKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("状态键不能为空");
        }
        return WorkflowInstanceState.valueOf(key);
    }
}
