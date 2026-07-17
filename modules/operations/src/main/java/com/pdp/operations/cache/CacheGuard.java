package com.pdp.operations.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存防护组件（防穿透、防击穿、防雪崩、降级，FR-106）。
 *
 * <p>对应 spec.md FR-106（缓存故障时核心操作可降级）和缓存三大问题的防护：
 *
 * <p><strong>缓存穿透</strong>（Penetration）：查询不存在的键，请求穿透到数据库。
 * 防护：loader 返回 empty 时写入空值标记（短 TTL），后续相同键直接返回 empty 不查库。
 *
 * <p><strong>缓存击穿</strong>（Breakdown）：热键过期瞬间，大量并发请求同时回源。
 * 防护：基于 {@link java.util.concurrent.Semaphore} 的本地互斥锁（每个键一把），
 * 仅允许一个线程回源加载，其余线程等待结果。分布式场景由 {@link CachePort#putIfAbsent}
 * 实现分布式互斥锁。
 *
 * <p><strong>缓存雪崩</strong>（Avalanche）：大量缓存同时过期，请求穿透到数据库。
 * 防护：{@link CacheTtl#effectiveTtl()} 在基础 TTL 上叠加随机抖动，使过期时间分散。
 *
 * <p><strong>降级</strong>（FR-106）：缓存不可用时自动降级为回源，不影响核心操作正确性。
 * {@link CachePort} 异常被捕获，直接调用 loader 加载。
 *
 * <p><strong>不变量</strong>（spec.md）：缓存不得成为业务状态、权限或审批结论的唯一事实源，
 * 所有缓存数据 MUST 可从主库重建。
 */
public class CacheGuard {

    /** 空值标记 TTL（穿透防护，1 秒，避免权限变更延迟）。 */
    private static final CacheTtl NULL_MARKER_TTL = CacheTtl.fixed(java.time.Duration.ofSeconds(1));

    /** 击穿防护等待超时（500 毫秒）。 */
    private static final long LOCK_WAIT_MS = 500;

    /** 击穿防护每键信号量许可数（1 = 互斥）。 */
    private static final int LOCK_PERMITS = 1;

    private final CachePort distributedCache;
    private final ConcurrentHashMap<CacheKey, Semaphore> localLocks = new ConcurrentHashMap<>();
    private final boolean degradationEnabled;

    public CacheGuard(CachePort distributedCache) {
        this(distributedCache, true);
    }

    public CacheGuard(CachePort distributedCache, boolean degradationEnabled) {
        this.distributedCache = Objects.requireNonNull(distributedCache, "distributedCache 不能为 null");
        this.degradationEnabled = degradationEnabled;
    }

    /**
     * 获取或加载（含穿透/击穿/雪崩防护和降级）。
     *
     * <p>流程：
     * <ol>
     *   <li>查询分布式缓存；命中返回（区分空值标记）；</li>
     *   <li>缓存未命中时获取本地互斥锁（防击穿）；</li>
     *   <li>持锁线程二次查询缓存（防止排队期间已被其他线程填充）；</li>
     *   <li>仍未命中则调用 loader 加载；</li>
     *   <li>加载结果写入缓存（含空值标记，防穿透；TTL 含抖动，防雪崩）；</li>
     *   <li>缓存异常时降级为直接调用 loader（FR-106）。</li>
     * </ol>
     *
     * @param key        缓存键
     * @param ttl        TTL（含抖动）
     * @param valueClass 值类型
     * @param loader     缓存未命中时的加载器（MUST 不返回 null，用 Optional.empty 表示无值）
     * @return 缓存或加载的值
     */
    public <T> Optional<T> getOrLoad(CacheKey key, CacheTtl ttl, Class<T> valueClass,
                                     Supplier<Optional<T>> loader) {
        Objects.requireNonNull(key, "key 不能为 null");
        Objects.requireNonNull(ttl, "ttl 不能为 null");
        Objects.requireNonNull(valueClass, "valueClass 不能为 null");
        Objects.requireNonNull(loader, "loader 不能为 null");

        // 降级模式：缓存不可用直接回源
        if (degradationEnabled && !distributedCache.isHealthy()) {
            return loader.get();
        }

        // 1. 查询分布式缓存
        Optional<T> cached = safeGet(key, valueClass);
        if (cached.isPresent()) {
            return cached;
        }
        // 检查空值标记（穿透防护）
        if (safeExists(key)) {
            return Optional.empty();
        }

        // 2. 获取本地互斥锁（防击穿）
        Semaphore lock = localLocks.computeIfAbsent(key, k -> new Semaphore(LOCK_PERMITS, true));
        boolean acquired = false;
        try {
            acquired = lock.tryAcquire(LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                // 等待超时，降级为直接加载（不缓存，避免并发写冲突）
                return loader.get();
            }

            // 3. 二次查询缓存（防止排队期间已被其他线程填充）
            cached = safeGet(key, valueClass);
            if (cached.isPresent()) {
                return cached;
            }
            if (safeExists(key)) {
                return Optional.empty();
            }

            // 4. 加载
            Optional<T> loaded = loader.get();

            // 5. 写入缓存（含空值标记防穿透，TTL 含抖动防雪崩）
            if (loaded.isPresent()) {
                safePut(key, loaded.get(), ttl);
            } else {
                safePut(key, null, NULL_MARKER_TTL);
            }
            return loaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } finally {
            if (acquired) {
                lock.release();
            }
            // 清理无竞争的锁，避免内存泄漏
            if (lock.availablePermits() == LOCK_PERMITS && localLocks.get(key) == lock) {
                localLocks.remove(key, lock);
            }
        }
    }

    /**
     * 失效缓存键（权限撤销等场景）。
     *
     * @param key 缓存键
     */
    public void invalidate(CacheKey key) {
        Objects.requireNonNull(key, "key 不能为 null");
        try {
            distributedCache.delete(key);
        } catch (CacheException e) {
            // 降级：删除失败不影响正确性，缓存自然过期
        }
    }

    /**
     * 按命名空间批量失效（FR-124 权限撤销批量失效）。
     *
     * @param workspaceId 工作空间
     * @param namespace   命名空间
     */
    public long invalidateByNamespace(com.pdp.shared.context.WorkspaceId workspaceId, String namespace) {
        Objects.requireNonNull(namespace, "namespace 不能为 null");
        try {
            return distributedCache.deleteByNamespace(workspaceId, namespace);
        } catch (CacheException e) {
            return 0;
        }
    }

    public boolean isHealthy() {
        return distributedCache.isHealthy();
    }

    // ==================== 内部辅助（降级安全包装） ====================

    private <T> Optional<T> safeGet(CacheKey key, Class<T> valueClass) {
        try {
            return distributedCache.get(key, valueClass);
        } catch (CacheException e) {
            return Optional.empty();
        }
    }

    private boolean safeExists(CacheKey key) {
        try {
            return distributedCache.exists(key);
        } catch (CacheException e) {
            return false;
        }
    }

    private <T> void safePut(CacheKey key, T value, CacheTtl ttl) {
        try {
            distributedCache.put(key, value, ttl);
        } catch (CacheException e) {
            // 降级：写入失败不影响正确性
        }
    }
}
