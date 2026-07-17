package com.pdp.operations.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地内存缓存（进程内缓存，FR-124 权限缓存 5 秒 SLA）。
 *
 * <p>对应 spec.md FR-124："本地权限缓存最长 5 秒失效"和 SC-036。
 * 本类提供进程内 TTL 缓存，用于权限判定等需要低延迟且可容忍短时不一致的场景。
 *
 * <p><strong>与分布式缓存的关系</strong>：
 * <ul>
 *   <li>本地缓存用于高频读、短 TTL（≤ 5 秒）的权限/配置数据；</li>
 *   <li>分布式缓存（{@link CachePort}）用于跨实例共享、较长 TTL 的视图/配置数据；</li>
 *   <li>本地缓存不作为唯一事实源（spec.md 不变量），所有数据可从主库重建。</li>
 * </ul>
 *
 * <p><strong>TTL 强制</strong>：{@link #put} 校验 TTL 不超过 {@code maxTtl}（默认 5 秒，
 * 权限缓存 SLA），超出抛出 {@link CacheException.Reason#TTL_VIOLATION}。
 *
 * <p><strong>清理策略</strong>：惰性清理（读取时检查过期）+ 定期清理（{@link #cleanup}）。
 * 使用 {@link ConcurrentHashMap} 保证线程安全，无外部依赖。
 *
 * <p><strong>降级</strong>（FR-106）：本地缓存异常 MUST 不影响核心操作，
 * {@link #get} 和 {@link #put} 异常时调用方应回源主库。
 */
public class LocalCache {

    /** FR-124：权限缓存默认最大 TTL（5 秒）。 */
    public static final Duration DEFAULT_MAX_TTL = Duration.ofSeconds(5);

    private final Map<CacheKey, Entry<?>> store = new ConcurrentHashMap<>();
    private final Duration maxTtl;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    /**
     * 构造本地缓存。
     *
     * @param maxTtl 最大允许 TTL（权限缓存 5 秒，超出拒绝写入）
     */
    public LocalCache(Duration maxTtl) {
        this.maxTtl = Objects.requireNonNull(maxTtl, "maxTtl 不能为 null");
        if (maxTtl.isZero() || maxTtl.isNegative()) {
            throw new IllegalArgumentException("maxTtl 必须为正: " + maxTtl);
        }
    }

    /** 默认本地缓存（FR-124：5 秒 TTL 上限）。 */
    public static LocalCache forPermission() {
        return new LocalCache(DEFAULT_MAX_TTL);
    }

    /**
     * 读取缓存值。
     *
     * <p>过期条目返回 empty 并惰性清理。空值标记（{@link NullMarker}）返回 empty 但不计 miss。
     *
     * @param key        缓存键
     * @param valueClass 值类型
     * @return 缓存值，不存在或已过期返回 empty
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CacheKey key, Class<T> valueClass) {
        Objects.requireNonNull(key, "key 不能为 null");
        Objects.requireNonNull(valueClass, "valueClass 不能为 null");

        Entry<?> entry = store.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        if (entry.isExpired(Instant.now())) {
            store.remove(key);
            evictionCount.incrementAndGet();
            missCount.incrementAndGet();
            return Optional.empty();
        }
        hitCount.incrementAndGet();
        if (entry.value() instanceof NullMarker) {
            return Optional.empty();
        }
        return Optional.ofNullable((T) entry.value());
    }

    /**
     * 写入缓存值（带 TTL）。
     *
     * @param key   缓存键
     * @param value 缓存值（null 写入空值标记，用于穿透防护）
     * @param ttl   TTL（MUST ≤ maxTtl）
     * @throws CacheException TTL 超过 maxTtl
     */
    public <T> void put(CacheKey key, T value, CacheTtl ttl) {
        Objects.requireNonNull(key, "key 不能为 null");
        Objects.requireNonNull(ttl, "ttl 不能为 null");

        Duration effectiveTtl = ttl.effectiveTtl();
        if (effectiveTtl.compareTo(maxTtl) > 0) {
            throw CacheException.ttlViolation(key,
                    effectiveTtl.getSeconds(), maxTtl.getSeconds());
        }

        Instant expiresAt = Instant.now().plus(effectiveTtl);
        Object stored = value != null ? value : NullMarker.INSTANCE;
        store.put(key, new Entry<>(stored, expiresAt));
    }

    /**
     * 写入空值标记（穿透防护）。
     *
     * <p>对应缓存穿透防护：查询不存在的键时，写入短 TTL 空值标记，防止恶意请求穿透到数据库。
     *
     * @param key     缓存键
     * @param ttl     空值标记 TTL（建议 ≤ 1 秒，避免权限变更延迟）
     */
    public void putNullMarker(CacheKey key, CacheTtl ttl) {
        put(key, null, ttl);
    }

    /** 删除缓存键。 */
    public boolean delete(CacheKey key) {
        Objects.requireNonNull(key, "key 不能为 null");
        Entry<?> removed = store.remove(key);
        if (removed != null) {
            evictionCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /** 按命名空间批量删除（权限撤销时批量失效）。 */
    public long deleteByNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace 不能为 null");
        long count = 0;
        var iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (namespace.equals(entry.getKey().namespace())) {
                iterator.remove();
                count++;
                evictionCount.incrementAndGet();
            }
        }
        return count;
    }

    /** 按工作空间批量删除（工作空间删除或隔离）。 */
    public long deleteByWorkspace(com.pdp.shared.context.WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        long count = 0;
        var iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (workspaceId.equals(entry.getKey().workspaceId())) {
                iterator.remove();
                count++;
                evictionCount.incrementAndGet();
            }
        }
        return count;
    }

    /** 清理所有过期条目（定期调用，避免内存泄漏）。 */
    public int cleanup() {
        int removed = 0;
        Instant now = Instant.now();
        var iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(now)) {
                iterator.remove();
                removed++;
                evictionCount.incrementAndGet();
            }
        }
        return removed;
    }

    /** 当前缓存条目数（含可能过期但未清理的）。 */
    public int size() {
        return store.size();
    }

    public long hitCount() {
        return hitCount.get();
    }

    public long missCount() {
        return missCount.get();
    }

    public long evictionCount() {
        return evictionCount.get();
    }

    /** 缓存命中率（0.0~1.0）。 */
    public double hitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    public Duration maxTtl() {
        return maxTtl;
    }

    /** 清空所有缓存（测试用）。 */
    public void invalidateAll() {
        store.clear();
    }

    /** 缓存条目内部表示。 */
    private record Entry<T>(T value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    /** 空值标记（穿透防护，区分"未缓存"和"缓存了 null"）。 */
    private static final class NullMarker {
        static final NullMarker INSTANCE = new NullMarker();

        @Override
        public String toString() {
            return "NullMarker";
        }
    }
}
