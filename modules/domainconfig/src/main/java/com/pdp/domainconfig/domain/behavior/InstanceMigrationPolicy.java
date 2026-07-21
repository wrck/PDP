package com.pdp.domainconfig.domain.behavior;

/**
 * 工作流绑定的运行实例迁移策略（FR-173、domain-package.schema.json workflowBindingDefinition.instanceMigrationPolicy）。
 *
 * <p>当领域包版本升级且工作流定义变化时，决定存量运行实例的处理方式。
 */
public enum InstanceMigrationPolicy {
    /** 锁定：运行实例保持创建时绑定的版本，不随包升级。 */
    PINNED,
    /** 人工审核：升级前必须由人工评估每个运行实例的迁移路径。 */
    MANUAL_REVIEW,
    /** 分批迁移：可由 DomainPackageMigrationService 自动分批迁移。 */
    BATCH_MIGRATABLE
}
