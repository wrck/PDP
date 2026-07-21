package com.pdp.domainconfig.domain.behavior;

/**
 * 工作流绑定触发器（FR-173、domain-package.schema.json workflowBindingDefinition.trigger）。
 */
public enum WorkflowBindingTrigger {
    /** 手动触发。 */
    MANUAL,
    /** 对象创建时触发。 */
    OBJECT_CREATED,
    /** 状态迁移时触发（需指定 transitionKey 通过变量映射传入）。 */
    STATE_TRANSITION,
    /** 领域事件触发（需指定 eventType）。 */
    DOMAIN_EVENT
}
