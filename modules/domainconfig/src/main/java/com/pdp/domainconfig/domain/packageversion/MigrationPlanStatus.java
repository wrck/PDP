package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包迁移计划状态（FR-168 高风险操作框架）。
 *
 * <p>状态机：
 * <ul>
 *   <li>{@link #DRAFT} → {@link #READY}（绑定影响预览并完成校验后进入就绪）</li>
 *   <li>{@link #READY} → {@link #RUNNING}（开始执行分批迁移）</li>
 *   <li>{@link #RUNNING} ↔ {@link #PAUSED}（人工暂停 / 恢复）</li>
 *   <li>{@link #RUNNING} → {@link #COMPLETED}（全部批次成功）</li>
 *   <li>{@link #RUNNING} → {@link #FAILED}（失败隔离策略为 ABORT_ALL 时整批失败）</li>
 *   <li>{@link #COMPLETED}/{@link #FAILED} → {@link #ROLLED_BACK}（在可回滚时间窗内回滚）</li>
 * </ul>
 */
public enum MigrationPlanStatus {
    DRAFT,
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    ROLLED_BACK
}
