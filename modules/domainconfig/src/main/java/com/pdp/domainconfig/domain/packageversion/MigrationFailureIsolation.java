package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包迁移失败隔离策略（FR-168 高风险操作框架）。
 */
public enum MigrationFailureIsolation {
    /** 立即中止：单个实例失败后立即中止整个迁移计划。 */
    ABORT_ALL,
    /** 隔离失败：失败实例隔离后继续其他实例迁移。 */
    ISOLATE_FAILED_KEEP_OTHERS,
    /** 人工审核：失败后暂停计划等待人工决策。 */
    MANUAL_REVIEW
}
