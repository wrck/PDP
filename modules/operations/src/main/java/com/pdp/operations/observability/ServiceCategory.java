package com.pdp.operations.observability;

import java.time.Duration;

/**
 * 平台服务类别枚举（版本化服务目录，FR-165/SC-016/SC-037）。
 *
 * <p>对应 spec.md "P1 服务目录"表，定义 5 类服务的适用交互时限和可用性目标。
 * 月度可用性统计 MUST 使用版本化服务目录维护（spec.md：每项请求明确服务类别、时限、
 * 责任团队和允许排除原因）。
 *
 * <p><strong>交互时限</strong>（SC-018）：
 * <ul>
 *   <li>{@link #CORE_DELIVERY_COMMAND}：核心交付命令，95% 在 2 秒内返回；</li>
 *   <li>{@link #CORE_DELIVERY_QUERY}：核心交付查询，95% 在 2 秒内呈现；</li>
 *   <li>{@link #OTHER_ONLINE_MANAGEMENT}：其他在线管理，95% 在 3 秒内；</li>
 *   <li>{@link #SEARCH_AND_STATISTICS}：搜索和基础统计，95% 在 3 秒内或 1 秒内渐进；</li>
 *   <li>{@link #BACKGROUND_JOB}：后台作业，5 秒内返回作业标识（不以同步时长衡量）。</li>
 * </ul>
 *
 * <p><strong>可用性目标</strong>（FR-102/FR-103/SC-016）：
 * <ul>
 *   <li>核心交付（命令+查询）：月度可用性 ≥ 99.95%；</li>
 *   <li>其他在线管理 + 搜索统计：月度可用性 ≥ 99.9%。</li>
 * </ul>
 */
public enum ServiceCategory {

    /** 核心交付命令：项目/阶段推进、任务更新、审批办理、交付件提交与发布。 */
    CORE_DELIVERY_COMMAND("CORE_DELIVERY_COMMAND",
            Duration.ofSeconds(2), true, "99.95%",
            "不得依赖搜索、报表或非关键外部系统"),

    /** 核心交付查询：项目概览、任务与待办、审批和交付件详情。 */
    CORE_DELIVERY_QUERY("CORE_DELIVERY_QUERY",
            Duration.ofSeconds(2), true, "99.95%",
            "缓存失效时回源或明确降级"),

    /** 其他在线管理：配置设计、审计检索、运维控制台、迁移控制台。 */
    OTHER_ONLINE_MANAGEMENT("OTHER_ONLINE_MANAGEMENT",
            Duration.ofSeconds(3), false, "99.9%",
            "失败必须可重试或人工补偿"),

    /** 搜索和基础统计：权限搜索、项目基础统计和跨视图汇总。 */
    SEARCH_AND_STATISTICS("SEARCH_AND_STATISTICS",
            Duration.ofSeconds(3), false, "99.9%",
            "不可用时核心流程保留人工导航"),

    /** 后台作业：导入、导出、归档、统计、投影重建和迁移。 */
    BACKGROUND_JOB("BACKGROUND_JOB",
            Duration.ofSeconds(5), false, "N/A",
            "不以同步请求时长衡量，按作业接受率、完成率和恢复能力衡量");

    private final String stableKey;
    private final Duration interactionTimeLimit;
    private final boolean core;
    private final String availabilityTarget;
    private final String failureHandling;

    ServiceCategory(String stableKey, Duration interactionTimeLimit, boolean core,
                    String availabilityTarget, String failureHandling) {
        this.stableKey = stableKey;
        this.interactionTimeLimit = interactionTimeLimit;
        this.core = core;
        this.availabilityTarget = availabilityTarget;
        this.failureHandling = failureHandling;
    }

    public String stableKey() {
        return stableKey;
    }

    /** 适用交互时限（P95 目标）。 */
    public Duration interactionTimeLimit() {
        return interactionTimeLimit;
    }

    /** 是否为核心服务（纳入 99.95% 可用性）。 */
    public boolean isCore() {
        return core;
    }

    /** 可用性目标（月度）。 */
    public String availabilityTarget() {
        return availabilityTarget;
    }

    /** 失败处理策略。 */
    public String failureHandling() {
        return failureHandling;
    }

    public static ServiceCategory fromStableKey(String stableKey) {
        for (ServiceCategory c : values()) {
            if (c.stableKey.equals(stableKey)) {
                return c;
            }
        }
        throw new IllegalArgumentException("未知服务类别稳定键: " + stableKey);
    }
}
