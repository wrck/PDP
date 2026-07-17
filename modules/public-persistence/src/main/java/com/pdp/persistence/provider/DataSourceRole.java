package com.pdp.persistence.provider;

/**
 * 数据源角色。
 *
 * <p>{@link #PDP_PRIMARY} 与 {@link #WORKFLOW_ENGINE} 唯一且始终存在；
 * 迁移源/目标必须绑定迁移计划和有效期。
 */
public enum DataSourceRole {
    PDP_PRIMARY,
    PDP_READ,
    WORKFLOW_ENGINE,
    MIGRATION_SOURCE,
    MIGRATION_TARGET
}
