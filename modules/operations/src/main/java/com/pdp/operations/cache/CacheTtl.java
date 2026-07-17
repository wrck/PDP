package com.pdp.operations.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存 TTL 值对象（含雪崩防护抖动）。
 *
 * <p>对应 spec.md FR-106（缓存故障时核心操作可降级）和缓存雪崩防护：大量缓存同时过期会导致
 * 请求穿透到数据库。本类在基础 TTL 上叠加随机抖动，使过期时间分散。
 *
 * <p><strong>FR-124 权限缓存 TTL</strong>：本地权限缓存最长 5 秒（{@link #PERMISSION_LOCAL_TTL}）。
 *
 * <p><strong>抖动策略</strong>：抖动幅度为基础 TTL 的 10%~30%（随机），避免可预测的批量过期。
 *
 * @param baseTtl    基础 TTL
 * @param jitterRatio 抖动比例（0.0~1.0，建议 0.1~0.3）
 */
public record CacheTtl(Duration baseTtl, double jitterRatio) {

    /** FR-124：本地权限缓存最长 5 秒。 */
    public static final Duration PERMISSION_LOCAL_TTL = Duration.ofSeconds(5);

    /** 默认抖动比例（20%）。 */
    public static final double DEFAULT_JITTER_RATIO = 0.2;

    /** 视图缓存默认 TTL（1 分钟）。 */
    public static final Duration DEFAULT_VIEW_TTL = Duration.ofMinutes(1);

    /** 配置缓存默认 TTL（5 分钟）。 */
    public static final Duration DEFAULT_CONFIG_TTL = Duration.ofMinutes(5);

    public CacheTtl {
        Objects.requireNonNull(baseTtl, "baseTtl 不能为 null");
        if (baseTtl.isZero() || baseTtl.isNegative()) {
            throw new IllegalArgumentException("baseTtl 必须为正: " + baseTtl);
        }
        if (jitterRatio < 0.0 || jitterRatio > 1.0) {
            throw new IllegalArgumentException("jitterRatio 必须在 [0.0, 1.0]: " + jitterRatio);
        }
    }

    /** 无抖动 TTL。 */
    public static CacheTtl fixed(Duration baseTtl) {
        return new CacheTtl(baseTtl, 0.0);
    }

    /** 默认抖动 TTL（20%）。 */
    public static CacheTtl withDefaultJitter(Duration baseTtl) {
        return new CacheTtl(baseTtl, DEFAULT_JITTER_RATIO);
    }

    /** 权限缓存 TTL（FR-124：5 秒，无抖动保证 SLA）。 */
    public static CacheTtl forPermissionLocal() {
        return new CacheTtl(PERMISSION_LOCAL_TTL, 0.0);
    }

    /** 视图缓存 TTL（1 分钟 + 20% 抖动）。 */
    public static CacheTtl forView() {
        return withDefaultJitter(DEFAULT_VIEW_TTL);
    }

    /** 配置缓存 TTL（5 分钟 + 20% 抖动）。 */
    public static CacheTtl forConfig() {
        return withDefaultJitter(DEFAULT_CONFIG_TTL);
    }

    /**
     * 计算实际 TTL（基础 + 随机抖动）。
     *
     * <p>抖动范围：{@code [baseTtl, baseTtl * (1 + jitterRatio)]}。
     * 抖动为 0 时返回基础 TTL（权限缓存保证 SLA）。
     *
     * @return 实际 TTL
     */
    public Duration effectiveTtl() {
        if (jitterRatio == 0.0) {
            return baseTtl;
        }
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(jitterRatio);
        long effectiveMillis = (long) (baseTtl.toMillis() * factor);
        return Duration.ofMillis(effectiveMillis);
    }

    /** 基础 TTL（不含抖动）。 */
    public Duration baseTtl() {
        return baseTtl;
    }

    /** 抖动比例。 */
    public double jitterRatio() {
        return jitterRatio;
    }
}
