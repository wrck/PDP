package com.pdp.operations.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 分布式缓存端口（六边形架构出站端口，Redis 抽象）。
 *
 * <p>对应 research.md：Redis 作为分布式缓存和会话存储。本端口屏蔽 Redis SDK，
 * 业务模块通过此端口读写缓存，不依赖 Redisson/Lettuce 等具体实现。
 *
 * <p><strong>核心契约</strong>：
 * <ul>
 *   <li><b>工作空间隔离</b>：所有键通过 {@link CacheKey} 携带工作空间前缀，禁止跨空间访问；</li>
 *   <li><b>降级友好</b>：缓存不可用时抛出 {@link CacheException}，调用方捕获后回源（FR-106）；</li>
 *   <li><b>TTL 强制</b>：权限缓存 TTL ≤ 5 秒（FR-124），超出抛出 {@link CacheException.Reason#TTL_VIOLATION}。</li>
 * </ul>
 *
 * <p><strong>不可作为唯一事实源</strong>（spec.md 不变量）：缓存不得成为业务状态、权限或审批结论的
 * 唯一事实源，所有缓存数据 MUST 可从主库重建。
 *
 * <p>实现由 {@code public-persistence} 或独立 infrastructure 模块提供（Redis 适配器）。
 */
public interface CachePort {

    /**
     * 读取缓存值。
     *
     * @param key        缓存键
     * @param valueClass 值类型（用于反序列化）
     * @return 缓存值，不存在返回 empty
     * @throws CacheException 缓存不可用或反序列化失败
     */
    <T> Optional<T> get(CacheKey key, Class<T> valueClass);

    /**
     * 写入缓存值（带 TTL）。
     *
     * @param key    缓存键
     * @param value  缓存值（null 表示空值标记，用于穿透防护）
     * @param ttl    TTL
     * @throws CacheException 缓存不可用或 TTL 违反约束
     */
    <T> void put(CacheKey key, T value, CacheTtl ttl);

    /**
     * 写入缓存值（仅当键不存在时，用于分布式锁）。
     *
     * @param key    缓存键
     * @param value  缓存值
     * @param ttl    TTL
     * @return true=写入成功（键原本不存在）；false=键已存在
     * @throws CacheException 缓存不可用
     */
    <T> boolean putIfAbsent(CacheKey key, T value, CacheTtl ttl);

    /**
     * 删除缓存键。
     *
     * @param key 缓存键
     * @return true=删除成功；false=键不存在
     * @throws CacheException 缓存不可用
     */
    boolean delete(CacheKey key);

    /**
     * 按命名空间批量删除缓存（权限撤销时批量失效）。
     *
     * <p>对应 FR-124：权限撤销后本地缓存 5 秒内失效。分布式缓存通过此方法批量失效。
     * 实现可使用 Redis SCAN + DEL 或维护命名空间索引。
     *
     * @param workspaceId 工作空间（null 表示平台级）
     * @param namespace   命名空间（如 {@code permission}）
     * @return 删除的键数
     * @throws CacheException 缓存不可用
     */
    long deleteByNamespace(com.pdp.shared.context.WorkspaceId workspaceId, String namespace);

    /**
     * 校验键是否存在。
     *
     * @param key 缓存键
     * @return true=存在
     * @throws CacheException 缓存不可用
     */
    boolean exists(CacheKey key);

    /**
     * 缓存是否健康（用于就绪检查和降级判断）。
     *
     * <p>缓存不可用时，{@link CacheGuard} 自动降级为回源，不影响核心操作（FR-106）。
     *
     * @return true=缓存可用
     */
    boolean isHealthy();

    /**
     * 获取或加载（便捷方法，含穿透防护）。
     *
     * <p>缓存命中返回值；未命中调用 loader 加载并写入缓存。loader 返回 null 时，
     * 写入空值标记（短 TTL）防止穿透。
     *
     * @param key        缓存键
     * @param ttl        TTL
     * @param valueClass 值类型
     * @param loader     缓存未命中时的加载器
     * @return 缓存或加载的值（可能为 null）
     * @throws CacheException 缓存不可用（loader 异常向上传播）
     */
    default <T> Optional<T> getOrLoad(CacheKey key, CacheTtl ttl, Class<T> valueClass,
                                      Supplier<Optional<T>> loader) {
        Optional<T> cached = get(key, valueClass);
        if (cached.isPresent()) {
            return cached;
        }
        // 检查空值标记
        if (exists(key)) {
            return Optional.empty();
        }
        Optional<T> loaded = loader.get();
        put(key, loaded.orElse(null), ttl);
        return loaded;
    }
}
