package com.pdp.workflow.domain;

/**
 * 工作流部署记录状态机枚举（FR-174、ADR-0005 第 7 节）。
 *
 * <p>状态迁移路径：
 * <pre>
 *   DEPLOYED → SUPERSEDED
 *      ↓
 *   FAILED
 * </pre>
 *
 * <p><strong>状态语义</strong>：
 * <ul>
 *   <li>{@link #DEPLOYED}：已成功部署到引擎，关联定义可启动新实例；</li>
 *   <li>{@link #SUPERSEDED}：被同键新版本部署取代，禁止启动新实例（运行中实例继续）；</li>
 *   <li>{@link #FAILED}：部署失败（引擎故障或内容哈希不匹配），需人工介入。</li>
 * </ul>
 */
public enum WorkflowDeploymentStatus {
    DEPLOYED,
    SUPERSEDED,
    FAILED;

    /** 稳定键（持久化使用，禁止依赖枚举序号）。 */
    public String stableKey() {
        return name();
    }

    /** 是否可启动新实例（仅 DEPLOYED 状态）。 */
    public boolean isActive() {
        return this == DEPLOYED;
    }

    public boolean isTerminal() {
        return this == SUPERSEDED || this == FAILED;
    }

    public static WorkflowDeploymentStatus fromStableKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("状态键不能为空");
        }
        return WorkflowDeploymentStatus.valueOf(key);
    }
}
