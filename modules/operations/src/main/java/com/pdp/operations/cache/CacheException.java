package com.pdp.operations.cache;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

/**
 * 缓存异常。
 *
 * <p>对应 spec.md FR-106（缓存故障时核心操作可降级）和 FR-124（权限缓存失效）。
 * 所有缓存失败 MUST 携带稳定原因分类，调用方据此决定降级策略：
 * <ul>
 *   <li>{@link Reason#CACHE_UNAVAILABLE}：分布式缓存不可用，降级到本地缓存或回源；</li>
 *   <li>{@link Reason#LOCAL_CACHE_UNAVAILABLE}：本地缓存不可用，回源（不影响正确性）；</li>
 *   <li>{@link Reason#SERIALIZATION_FAILED}：序列化失败，跳过缓存回源；</li>
 *   <li>{@link Reason#LOCK_ACQUISITION_FAILED}：击穿防护互斥锁获取失败，降级为短 TTL 缓存或回源；</li>
 *   <li>{@link Reason#TTL_VIOLATION}：TTL 违反约束（如权限缓存 > 5 秒），拒绝缓存。</li>
 * </ul>
 *
 * <p><strong>降级原则</strong>（FR-106）：缓存异常 MUST 不影响核心操作正确性，
 * 调用方捕获后回源数据库。错误码使用 {@link ErrorCode#SERVICE_UNAVAILABLE}，
 * 但缓存异常通常不直接返回给客户端（内部降级处理）。
 */
public class CacheException extends PdpException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final CacheKey cacheKey;

    public enum Reason {
        /** 分布式缓存不可用（Redis 网络故障、超时）。 */
        CACHE_UNAVAILABLE("CACHE.UNAVAILABLE"),
        /** 本地缓存不可用（内存不足、序列化失败）。 */
        LOCAL_CACHE_UNAVAILABLE("CACHE.LOCAL_UNAVAILABLE"),
        /** 序列化/反序列化失败。 */
        SERIALIZATION_FAILED("CACHE.SERIALIZATION_FAILED"),
        /** 击穿防护互斥锁获取失败。 */
        LOCK_ACQUISITION_FAILED("CACHE.LOCK_ACQUISITION_FAILED"),
        /** TTL 违反约束（如权限缓存 > 5 秒）。 */
        TTL_VIOLATION("CACHE.TTL_VIOLATION");

        private final String stableKey;

        Reason(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }
    }

    public CacheException(Reason reason, CacheKey cacheKey, String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
        this.reason = reason;
        this.cacheKey = cacheKey;
    }

    public CacheException(Reason reason, CacheKey cacheKey, String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
        this.reason = reason;
        this.cacheKey = cacheKey;
    }

    public Reason reason() {
        return reason;
    }

    public CacheKey cacheKey() {
        return cacheKey;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        return "[" + reason.stableKey() + "] " + base
                + (cacheKey != null ? " (key=" + cacheKey + ")" : "");
    }

    public static CacheException cacheUnavailable(CacheKey key, String message, Throwable cause) {
        return new CacheException(Reason.CACHE_UNAVAILABLE, key, message, cause);
    }

    public static CacheException localCacheUnavailable(CacheKey key, String message, Throwable cause) {
        return new CacheException(Reason.LOCAL_CACHE_UNAVAILABLE, key, message, cause);
    }

    public static CacheException serializationFailed(CacheKey key, String message, Throwable cause) {
        return new CacheException(Reason.SERIALIZATION_FAILED, key, message, cause);
    }

    public static CacheException lockAcquisitionFailed(CacheKey key, String message) {
        return new CacheException(Reason.LOCK_ACQUISITION_FAILED, key, message);
    }

    public static CacheException ttlViolation(CacheKey key, long actualTtlSeconds, long maxTtlSeconds) {
        return new CacheException(Reason.TTL_VIOLATION, key,
                "TTL " + actualTtlSeconds + "s 超过上限 " + maxTtlSeconds + "s");
    }
}
