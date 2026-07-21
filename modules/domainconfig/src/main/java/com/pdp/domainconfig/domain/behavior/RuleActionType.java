package com.pdp.domainconfig.domain.behavior;

/**
 * 规则动作类型（domain-package.schema.json ruleDefinition.actions[].type）。
 *
 * <p>FR-167 确定性状态机约束规则可触发的动作集合；规则动作不得绕过权限模型，
 * 由 {@code DomainPackageValidationService}（T121）发布前校验。
 */
public enum RuleActionType {
    /** 设置字段值（受权限模型约束）。 */
    SET_FIELD,
    /** 创建对象实例。 */
    CREATE_OBJECT,
    /** 触发状态迁移（必须存在对应 {@link TransitionDefinition}）。 */
    TRANSITION,
    /** 提交审批（必须存在对应 approvalDefinition）。 */
    SUBMIT_APPROVAL,
    /** 发送通知。 */
    NOTIFY,
    /** 调用受治理扩展（必须存在对应 {@link ExtensionDefinition}）。 */
    CALL_EXTENSION,
    /** 发出领域事件。 */
    EMIT_EVENT
}
