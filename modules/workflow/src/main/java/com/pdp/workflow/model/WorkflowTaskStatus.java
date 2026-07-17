package com.pdp.workflow.model;

/**
 * 平台人工任务状态机枚举（FR-174）。
 *
 * <p>状态迁移路径：
 * <pre>
 *   CREATED → ASSIGNED → IN_PROGRESS → COMPLETED
 *                ↓           ↓
 *             DELEGATED    CANCELLED
 *                ↓
 *           （回到 ASSIGNED）
 * </pre>
 *
 * <p><strong>状态语义</strong>：
 * <ul>
 *   <li>{@link #CREATED}：任务已创建，待认领；</li>
 *   <li>{@link #ASSIGNED}：已分配给候选人；</li>
 *   <li>{@link #IN_PROGRESS}：办理中；</li>
 *   <li>{@link #DELEGATED}：已委派给其他用户；</li>
 *   <li>{@link #COMPLETED}：已完成（终态）；</li>
 *   <li>{@link #CANCELLED}：已取消（终态，如流程终止）。</li>
 * </ul>
 */
public enum WorkflowTaskStatus {
    CREATED,
    ASSIGNED,
    IN_PROGRESS,
    DELEGATED,
    COMPLETED,
    CANCELLED;

    public String stableKey() {
        return name();
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(WorkflowTaskStatus target) {
        return switch (this) {
            case CREATED -> target == ASSIGNED || target == CANCELLED;
            case ASSIGNED -> target == IN_PROGRESS || target == DELEGATED
                    || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case DELEGATED -> target == ASSIGNED || target == IN_PROGRESS
                    || target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static WorkflowTaskStatus fromStableKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("状态键不能为空");
        }
        return WorkflowTaskStatus.valueOf(key);
    }
}
