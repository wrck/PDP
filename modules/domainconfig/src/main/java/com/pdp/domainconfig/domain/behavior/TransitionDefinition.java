package com.pdp.domainconfig.domain.behavior;

/**
 * 状态迁移定义（domain-package.schema.json transitionDefinition）。
 *
 * <p>FR-167 确定性状态机：每个迁移 MUST 定义前置条件（{@link #requiredPermission}、
 * {@link #guardRuleKey}）、权限、并发语义、结果（{@link #to}）和稳定失败原因。
 *
 * <p>{@link #reversible} 控制是否允许回滚迁移；{@link #auditLevel} 为 {@link AuditLevel#HIGH_RISK}
 * 时由 {@code DomainPackageMigrationService}（T124）走高风险操作框架。
 *
 * <p>所有 {@code *Key} 字段为对应定义的 stableKey 引用，由 {@code DomainPackageValidationService}
 * （T121）发布前校验可达性，避免不可达状态（SC-013）。
 */
public record TransitionDefinition(
        String stableKey,
        String from,
        String to,
        String requiredPermission,
        String guardRuleKey,
        String approvalDefinitionKey,
        String workflowBindingKey,
        boolean reversible,
        AuditLevel auditLevel) {

    public TransitionDefinition {
        if (stableKey == null || stableKey.isBlank()) {
            throw new IllegalArgumentException("stableKey 不能为空");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("from 不能为空");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to 不能为空");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("from 与 to 不能相同");
        }
        if (requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("requiredPermission 不能为空");
        }
        if (auditLevel == null) {
            auditLevel = AuditLevel.STANDARD;
        }
    }

    /** 是否为高风险迁移。 */
    public boolean isHighRisk() {
        return auditLevel == AuditLevel.HIGH_RISK;
    }
}
