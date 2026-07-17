package com.pdp.persistence.config;

/**
 * 平台数据源键常量。
 *
 * <p>对应 persistence-design.md 定义的数据源键：
 * <ul>
 *   <li>{@link #PDP_PRIMARY} — 在线业务、审计、事件发布、后台任务事实（写）</li>
 *   <li>{@link #PDP_READ} — 明确允许最终一致的查询（只读）</li>
 *   <li>{@link #MIGRATION_SOURCE} — 历史 MySQL 或数据库切换源（只读）</li>
 *   <li>{@link #MIGRATION_TARGET} — 数据库切换目标的预装载和核对（仅迁移执行器写）</li>
 *   <li>{@link #WORKFLOW_ENGINE} — Flowable Process Engine 运行表（仅 workflow 模块）</li>
 * </ul>
 *
 * <p>strict=true，找不到数据源时失败，不回退到默认库。
 * primary=pdpPrimary；未声明路由的业务调用始终访问主库。
 */
public final class DataSourceKeys {

    public static final String PDP_PRIMARY = "pdpPrimary";
    public static final String PDP_READ = "pdpRead";
    public static final String MIGRATION_SOURCE = "migrationSource";
    public static final String MIGRATION_TARGET = "migrationTarget";
    public static final String WORKFLOW_ENGINE = "workflowEngine";

    private DataSourceKeys() {
    }
}
