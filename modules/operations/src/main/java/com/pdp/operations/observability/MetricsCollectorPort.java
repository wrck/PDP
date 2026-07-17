package com.pdp.operations.observability;

import java.time.Duration;
import java.util.Map;

/**
 * 指标采集端口（六边形架构出站端口）。
 *
 * <p>对应 spec.md 可观测性基线和 SC-018 性能验收。屏蔽具体指标框架
 * （Micrometer/Prometheus），业务模块通过此端口采集计数器、仪表、直方图和计时器。
 *
 * <p><strong>指标类型</strong>：
 * <ul>
 *   <li>{@link #incrementCounter}：计数器（单调递增，如请求总数、错误数）；</li>
 *   <li>{@link #recordGauge}：仪表（当前值，如缓存大小、活跃连接数）；</li>
 *   <li>{@link #recordHistogram}：直方图（分布，如请求延迟分布）；</li>
 *   <li>{@link #recordTimer}：计时器（耗时，用于 P95/P99 计算，SC-018）。</li>
 * </ul>
 *
 * <p><strong>标签</strong>：指标 MUST 携带服务类别（{@link ServiceCategory}）和工作空间标签，
 * 便于按类别聚合可用性（FR-165）和按工作空间隔离。
 *
 * <p>实现由 {@code public-persistence} 或独立 infrastructure 模块提供
 * （Micrometer Prometheus 适配器）。
 */
public interface MetricsCollectorPort {

    /**
     * 递增计数器。
     *
     * @param name   指标名（如 {@code pdp.requests.total}）
     * @param tags   标签（如 service=core_delivery_command, workspace=uuid）
     * @param amount 递增量（必须 > 0）
     */
    void incrementCounter(String name, Map<String, String> tags, long amount);

    /** 递增计数器 1。 */
    default void incrementCounter(String name, Map<String, String> tags) {
        incrementCounter(name, tags, 1);
    }

    /**
     * 记录仪表值（当前值）。
     *
     * @param name  指标名（如 {@code pdp.cache.size}）
     * @param tags  标签
     * @param value 当前值
     */
    void recordGauge(String name, Map<String, String> tags, double value);

    /**
     * 记录直方图样本（分布统计）。
     *
     * @param name  指标名（如 {@code pdp.request.duration}）
     * @param tags  标签
     * @param value 样本值
     */
    void recordHistogram(String name, Map<String, String> tags, double value);

    /**
     * 记录计时器样本（耗时）。
     *
     * <p>用于 P95/P99 延迟计算（SC-018：核心交互 P95 ≤ 2 秒，搜索 P95 ≤ 3 秒）。
     *
     * @param name     指标名（如 {@code pdp.request.latency}）
     * @param tags     标签（MUST 包含 service 标签）
     * @param duration 耗时
     */
    void recordTimer(String name, Map<String, String> tags, Duration duration);

    /**
     * 记录请求结果（便捷方法，采集 FR-165 SLI 相关指标）。
     *
     * <p>采集请求总数、成功/失败计数、延迟直方图，按服务类别标签聚合。
     *
     * @param category 服务类别
     * @param operation 操作标识
     * @param succeeded 是否成功
     * @param duration  耗时
     * @param withinTimeLimit 是否在交互时限内
     */
    default void recordRequest(ServiceCategory category, String operation,
                               boolean succeeded, Duration duration, boolean withinTimeLimit) {
        Map<String, String> tags = Map.of(
                "service", category.stableKey(),
                "operation", operation,
                "core", String.valueOf(category.isCore()));
        incrementCounter("pdp.requests.total", tags);
        if (succeeded) {
            incrementCounter("pdp.requests.success", tags);
        } else {
            incrementCounter("pdp.requests.failure", tags);
        }
        if (!withinTimeLimit) {
            incrementCounter("pdp.requests.timeout", tags);
        }
        recordTimer("pdp.request.latency", tags, duration);
    }
}
