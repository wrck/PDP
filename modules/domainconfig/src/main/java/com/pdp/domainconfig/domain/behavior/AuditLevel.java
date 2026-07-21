package com.pdp.domainconfig.domain.behavior;

/**
 * 状态迁移审计级别（domain-package.schema.json transitionDefinition.auditLevel）。
 *
 * <p>{@link #HIGH_RISK} 触发 FR-168 高风险操作框架，MUST 提供影响预览、明确确认、
 * 审计以及撤销、回退或人工补偿路径。
 */
public enum AuditLevel {
    /** 标准审计：仅记录状态迁移与操作人。 */
    STANDARD,
    /** 高风险审计：必须走高风险操作框架（影响预览、确认、可回滚）。 */
    HIGH_RISK
}
