package com.pdp.operations.job;

import java.util.Objects;

/**
 * 后台作业类型枚举（稳定键）。
 *
 * <p>对应表 {@code background_job.job_type} 列。使用稳定字符串键持久化，禁止依赖枚举序号。
 * 每种类型对应一个 {@link JobHandler} 实现，由 {@link BackgroundJobCoordinator} 在调度时按类型分派。
 *
 * <p>P1 覆盖 spec.md 与 OpenAPI 契约中定义的后台作业类型：批量导入、导出、归档、统计、
 * 投影重建、历史迁移、集成补偿。领域包扩展的作业类型由领域包注册自定义 {@link JobHandler}，
 * 不进入平台核心枚举。
 */
public enum BackgroundJobType {

    /** 批量导入（任务、交付件、配置等）。 */
    BATCH_IMPORT("BATCH_IMPORT"),
    /** 批量导出（项目数据、审计、报表等）。 */
    BATCH_EXPORT("BATCH_EXPORT"),
    /** 归档（项目、交付件、审计等）。 */
    ARCHIVE("ARCHIVE"),
    /** 统计聚合（项目进度、里程碑、健康度等）。 */
    STATISTICS("STATISTICS"),
    /** 搜索投影重建（分析器版本升级或投影定义变更）。 */
    PROJECTION_REBUILD("PROJECTION_REBUILD"),
    /** 历史迁移批次（源库 → PDP）。 */
    MIGRATION("MIGRATION"),
    /** 集成补偿（外部系统失败重试或人工补偿）。 */
    INTEGRATION_COMPENSATION("INTEGRATION_COMPENSATION");

    private final String stableKey;

    BackgroundJobType(String stableKey) {
        this.stableKey = stableKey;
    }

    public String stableKey() {
        return stableKey;
    }

    public static BackgroundJobType fromStableKey(String stableKey) {
        Objects.requireNonNull(stableKey, "stableKey 不能为 null");
        for (BackgroundJobType t : values()) {
            if (t.stableKey.equals(stableKey)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知后台作业类型稳定键: " + stableKey);
    }
}
