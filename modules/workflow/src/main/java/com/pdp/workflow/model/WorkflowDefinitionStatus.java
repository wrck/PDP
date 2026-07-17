package com.pdp.workflow.model;

/**
 * 流程定义状态机枚举（FR-174）。
 *
 * <p>状态迁移路径：
 * <pre>
 *   VALIDATED → DEPLOYED → DEPRECATED → RETIRED
 *                  ↑          |             |
 *                  └──────────┘             |
 *                  （重新激活）               |
 *                                           ↓
 *                                       （终态，不可恢复）
 * </pre>
 *
 * <p><strong>状态语义</strong>：
 * <ul>
 *   <li>{@link #VALIDATED}：BPMN 校验通过，尚未部署；</li>
 *   <li>{@link #DEPLOYED}：已部署到 Flowable 引擎，可启动新实例；</li>
 *   <li>{@link #DEPRECATED}：已标记弃用，禁止启动新实例，已运行实例继续完成；</li>
 *   <li>{@link #RETIRED}：已下线，新实例无法启动，运行中实例需迁移或终止。</li>
 * </ul>
 */
public enum WorkflowDefinitionStatus {
    VALIDATED,
    DEPLOYED,
    DEPRECATED,
    RETIRED;

    /** 稳定键（持久化使用，禁止依赖枚举序号）。 */
    public String stableKey() {
        return name();
    }

    /**
     * 是否可启动新实例。
     *
     * @return true 表示 DEPLOYED 状态，可启动新实例
     */
    public boolean canStartInstance() {
        return this == DEPLOYED;
    }

    /**
     * 是否允许迁移到目标状态。
     *
     * @param target 目标状态
     * @return true 表示状态迁移合法
     */
    public boolean canTransitionTo(WorkflowDefinitionStatus target) {
        return switch (this) {
            case VALIDATED -> target == DEPLOYED || target == RETIRED;
            case DEPLOYED -> target == DEPRECATED || target == RETIRED;
            case DEPRECATED -> target == DEPLOYED || target == RETIRED;
            case RETIRED -> false; // 终态不可恢复
        };
    }

    public static WorkflowDefinitionStatus fromStableKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("状态键不能为空");
        }
        return WorkflowDefinitionStatus.valueOf(key);
    }
}
