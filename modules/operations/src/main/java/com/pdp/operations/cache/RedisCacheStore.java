package com.pdp.operations.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Redis 缓存、标签失效、generation 条件写和短租约的最小适配器边界。
 *
 * <p>实现必须保证 {@link #invalidate(CacheKey)} 与 {@link #putIfGeneration} 对同一键具有原子顺序，
 * 标签失效必须递增受影响键的 generation，防止失效期间的旧回源结果重新写回。连接、超时和脚本执行
 * 失败统一包装为 {@link RedisCacheException}。
 */
public interface RedisCacheStore {

  Optional<CacheEntry> get(CacheKey key);

  long generation(CacheKey key);

  boolean putIfGeneration(
      CacheKey key,
      long expectedGeneration,
      byte[] payload,
      Duration ttl,
      Set<String> invalidationTags);

  long invalidate(CacheKey key);

  long invalidateTag(String tag);

  boolean tryAcquireLoadLease(CacheKey key, String ownerToken, Duration ttl);

  void releaseLoadLease(CacheKey key, String ownerToken);

  record CacheEntry(byte[] payload, long generation, Instant expiresAt) {
    public CacheEntry {
      payload = Arrays.copyOf(payload, payload.length);
      if (generation < 0) {
        throw new IllegalArgumentException("generation 不得为负数");
      }
      if (expiresAt == null) {
        throw new IllegalArgumentException("expiresAt 不能为空");
      }
    }

    @Override
    public byte[] payload() {
      return Arrays.copyOf(payload, payload.length);
    }
  }

  final class RedisCacheException extends RuntimeException {
    public RedisCacheException(String message, Throwable cause) {
      super(message, cause);
    }

    public RedisCacheException(String message) {
      super(message);
    }
  }
}
