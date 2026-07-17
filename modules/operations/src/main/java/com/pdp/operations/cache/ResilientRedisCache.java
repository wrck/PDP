package com.pdp.operations.cache;

import com.pdp.operations.cache.RedisCacheStore.CacheEntry;
import com.pdp.operations.cache.RedisCacheStore.RedisCacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Redis 缓存旁路、失效传播、降级回源和防击穿组件。
 *
 * <p>Redis 不可用或熔断时直接调用权威 loader，核心事务不依赖缓存成功。本机并发请求共享
 * single-flight；不同节点通过 Redis 短租约协调。等待租约超时后允许独立回源，优先保证可用性。
 */
public final class ResilientRedisCache {
  public static final Duration AUTHORIZATION_MAX_TTL = Duration.ofSeconds(5);

  private final RedisCacheStore store;
  private final Clock clock;
  private final WaitStrategy waitStrategy;
  private final DegradationPolicy degradationPolicy;
  private final FailureCircuit failureCircuit;
  private final ConcurrentMap<FlightKey, CompletableFuture<CacheResult<?>>> flights =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<CacheKey, AtomicLong> localKeyEpochs = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> localTagEpochs = new ConcurrentHashMap<>();

  public ResilientRedisCache(
      RedisCacheStore store,
      Clock clock,
      WaitStrategy waitStrategy,
      DegradationPolicy degradationPolicy) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy");
    this.degradationPolicy = Objects.requireNonNull(degradationPolicy, "degradationPolicy");
    this.failureCircuit = new FailureCircuit(degradationPolicy);
  }

  public ResilientRedisCache(RedisCacheStore store, Clock clock) {
    this(store, clock, WaitStrategy.threadSleep(), new DegradationPolicy(3, Duration.ofSeconds(5)));
  }

  /**
   * 读取缓存或回源。loader 和 codec 异常属于业务/序列化失败并向上传播；只有
   * {@link RedisCacheException} 会触发缓存降级。
   */
  public <T> CacheResult<T> getOrLoad(
      CacheKey key,
      CachePolicy policy,
      Set<String> invalidationTags,
      CacheCodec<T> codec,
      Supplier<T> loader) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(policy, "policy");
    Set<String> tags = normalizeTags(invalidationTags);
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(loader, "loader");
    if (policy.cacheClass() == CacheClass.AUTHORIZATION) {
      if (key.workspaceId() == null) {
        throw new IllegalArgumentException("授权缓存必须绑定工作空间");
      }
      if (tags.isEmpty()) {
        throw new IllegalArgumentException("授权缓存必须提供失效标签");
      }
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      EpochSnapshot snapshot = snapshot(key, tags);
      CacheResult<T> result = singleFlight(snapshot, policy, codec, loader);
      if (snapshot.isCurrent(localKeyEpochs, localTagEpochs)) {
        return result;
      }
    }
    T latest = requireLoadedValue(loader.get());
    return new CacheResult<>(
        latest, Source.INVALIDATION_RACE_RELOAD, false, clock.instant());
  }

  public InvalidationResult invalidate(CacheKey key) {
    Objects.requireNonNull(key, "key");
    localKeyEpochs.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    Instant requestedAt = clock.instant();
    if (!failureCircuit.allowRedis(requestedAt)) {
      return new InvalidationResult(false, 0, requestedAt, "REDIS_CIRCUIT_OPEN");
    }
    try {
      long affected = store.invalidate(key);
      failureCircuit.recordSuccess();
      return new InvalidationResult(true, affected, requestedAt, null);
    } catch (RedisCacheException exception) {
      failureCircuit.recordFailure(requestedAt);
      return new InvalidationResult(false, 0, requestedAt, "REDIS_INVALIDATION_FAILED");
    }
  }

  public InvalidationResult invalidateTag(String tag) {
    String normalized = normalizeTag(tag);
    localTagEpochs.computeIfAbsent(normalized, ignored -> new AtomicLong()).incrementAndGet();
    Instant requestedAt = clock.instant();
    if (!failureCircuit.allowRedis(requestedAt)) {
      return new InvalidationResult(false, 0, requestedAt, "REDIS_CIRCUIT_OPEN");
    }
    try {
      long affected = store.invalidateTag(normalized);
      failureCircuit.recordSuccess();
      return new InvalidationResult(true, affected, requestedAt, null);
    } catch (RedisCacheException exception) {
      failureCircuit.recordFailure(requestedAt);
      return new InvalidationResult(false, 0, requestedAt, "REDIS_INVALIDATION_FAILED");
    }
  }

  @SuppressWarnings("unchecked")
  private <T> CacheResult<T> singleFlight(
      EpochSnapshot snapshot,
      CachePolicy policy,
      CacheCodec<T> codec,
      Supplier<T> loader) {
    FlightKey flightKey =
        new FlightKey(snapshot.key(), snapshot.keyEpoch(), snapshot.tagEpochs());
    CompletableFuture<CacheResult<?>> mine = new CompletableFuture<>();
    CompletableFuture<CacheResult<?>> existing = flights.putIfAbsent(flightKey, mine);
    if (existing != null) {
      try {
        return (CacheResult<T>) existing.join();
      } catch (CompletionException exception) {
        throw propagate(exception.getCause());
      }
    }
    try {
      CacheResult<T> result = resolve(snapshot, policy, codec, loader);
      mine.complete(result);
      return result;
    } catch (RuntimeException exception) {
      mine.completeExceptionally(exception);
      throw exception;
    } finally {
      flights.remove(flightKey, mine);
    }
  }

  private <T> CacheResult<T> resolve(
      EpochSnapshot snapshot,
      CachePolicy policy,
      CacheCodec<T> codec,
      Supplier<T> loader) {
    Instant now = clock.instant();
    if (!failureCircuit.allowRedis(now)) {
      return loaded(loader, Source.DEGRADED_LOADER, false);
    }

    Optional<CacheEntry> cached;
    try {
      cached = store.get(snapshot.key());
      failureCircuit.recordSuccess();
    } catch (RedisCacheException exception) {
      failureCircuit.recordFailure(now);
      return loaded(loader, Source.DEGRADED_LOADER, false);
    }
    if (cached.isPresent() && cached.get().expiresAt().isAfter(now)) {
      try {
        return new CacheResult<>(
            codec.decode(cached.get().payload()), Source.CACHE, true, now);
      } catch (RuntimeException exception) {
        invalidateCorruptEntry(snapshot.key(), now);
      }
    }

    long generation;
    String leaseOwner = UUID.randomUUID().toString();
    boolean leaseAcquired;
    try {
      generation = store.generation(snapshot.key());
      leaseAcquired =
          store.tryAcquireLoadLease(snapshot.key(), leaseOwner, policy.loadLeaseTtl());
      failureCircuit.recordSuccess();
    } catch (RedisCacheException exception) {
      failureCircuit.recordFailure(now);
      return loaded(loader, Source.DEGRADED_LOADER, false);
    }

    if (!leaseAcquired) {
      return waitForLeaseOwner(snapshot.key(), policy, codec, loader);
    }

    try {
      T value = requireLoadedValue(loader.get());
      boolean cacheWritten = false;
      if (snapshot.isCurrent(localKeyEpochs, localTagEpochs)) {
        try {
          cacheWritten =
              store.putIfGeneration(
                  snapshot.key(),
                  generation,
                  codec.encode(value),
                  policy.ttl(),
                  snapshot.tags());
          failureCircuit.recordSuccess();
        } catch (RedisCacheException exception) {
          failureCircuit.recordFailure(clock.instant());
        }
      }
      return new CacheResult<>(
          value,
          cacheWritten ? Source.LOADER : Source.LOADER_CACHE_WRITE_SKIPPED,
          cacheWritten,
          clock.instant());
    } finally {
      try {
        store.releaseLoadLease(snapshot.key(), leaseOwner);
        failureCircuit.recordSuccess();
      } catch (RedisCacheException exception) {
        failureCircuit.recordFailure(clock.instant());
      }
    }
  }

  private <T> CacheResult<T> waitForLeaseOwner(
      CacheKey key, CachePolicy policy, CacheCodec<T> codec, Supplier<T> loader) {
    for (int attempt = 0; attempt < policy.contentionRetries(); attempt++) {
      waitStrategy.await(policy.contentionWait());
      try {
        Optional<CacheEntry> cached = store.get(key);
        failureCircuit.recordSuccess();
        if (cached.isPresent() && cached.get().expiresAt().isAfter(clock.instant())) {
          try {
            return new CacheResult<>(
                codec.decode(cached.get().payload()),
                Source.CACHE_AFTER_LEASE_WAIT,
                true,
                clock.instant());
          } catch (RuntimeException exception) {
            invalidateCorruptEntry(key, clock.instant());
          }
        }
      } catch (RedisCacheException exception) {
        failureCircuit.recordFailure(clock.instant());
        return loaded(loader, Source.DEGRADED_LOADER, false);
      }
    }
    return loaded(loader, Source.CONTENTION_FALLBACK_LOADER, false);
  }

  private void invalidateCorruptEntry(CacheKey key, Instant now) {
    try {
      store.invalidate(key);
      failureCircuit.recordSuccess();
    } catch (RedisCacheException exception) {
      failureCircuit.recordFailure(now);
    }
  }

  private <T> CacheResult<T> loaded(
      Supplier<T> loader, Source source, boolean cacheAvailable) {
    return new CacheResult<>(
        requireLoadedValue(loader.get()), source, cacheAvailable, clock.instant());
  }

  private EpochSnapshot snapshot(CacheKey key, Set<String> tags) {
    long keyEpoch = localKeyEpochs.computeIfAbsent(key, ignored -> new AtomicLong()).get();
    Map<String, Long> tagEpochs = new TreeMap<>();
    for (String tag : tags) {
      tagEpochs.put(
          tag, localTagEpochs.computeIfAbsent(tag, ignored -> new AtomicLong()).get());
    }
    return new EpochSnapshot(key, keyEpoch, tags, Map.copyOf(tagEpochs));
  }

  private static Set<String> normalizeTags(Set<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return Set.of();
    }
    if (tags.size() > 32) {
      throw new IllegalArgumentException("单个缓存键最多允许 32 个失效标签");
    }
    LinkedHashMap<String, Boolean> normalized = new LinkedHashMap<>();
    for (String tag : tags) {
      normalized.put(normalizeTag(tag), Boolean.TRUE);
    }
    return Set.copyOf(normalized.keySet());
  }

  private static String normalizeTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    String normalized = tag.strip();
    if (normalized.isEmpty() || normalized.length() > 160) {
      throw new IllegalArgumentException("缓存失效标签长度无效");
    }
    return normalized;
  }

  private static <T> T requireLoadedValue(T value) {
    return Objects.requireNonNull(value, "缓存 loader 不得返回 null");
  }

  private static RuntimeException propagate(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new IllegalStateException("缓存 single-flight 执行失败", throwable);
  }

  public enum CacheClass {
    AUTHORIZATION,
    REBUILDABLE
  }

  public enum Source {
    CACHE,
    CACHE_AFTER_LEASE_WAIT,
    LOADER,
    LOADER_CACHE_WRITE_SKIPPED,
    CONTENTION_FALLBACK_LOADER,
    DEGRADED_LOADER,
    INVALIDATION_RACE_RELOAD
  }

  public record CachePolicy(
      CacheClass cacheClass,
      Duration ttl,
      Duration loadLeaseTtl,
      Duration contentionWait,
      int contentionRetries) {
    public CachePolicy {
      Objects.requireNonNull(cacheClass, "cacheClass");
      requirePositive(ttl, "ttl");
      requirePositive(loadLeaseTtl, "loadLeaseTtl");
      if (contentionWait == null || contentionWait.isNegative()) {
        throw new IllegalArgumentException("contentionWait 不得为负数");
      }
      if (contentionRetries < 0 || contentionRetries > 10) {
        throw new IllegalArgumentException("contentionRetries 必须在 0 到 10 之间");
      }
      if (cacheClass == CacheClass.AUTHORIZATION && ttl.compareTo(AUTHORIZATION_MAX_TTL) > 0) {
        throw new IllegalArgumentException("授权缓存 TTL 不得超过 5 秒");
      }
    }
  }

  public record CacheResult<T>(
      T value, Source source, boolean cacheAvailable, Instant resolvedAt) {
    public CacheResult {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(resolvedAt, "resolvedAt");
    }
  }

  public record InvalidationResult(
      boolean redisApplied,
      long affectedKeys,
      Instant requestedAt,
      String failureCode) {
    public InvalidationResult {
      if (affectedKeys < 0) {
        throw new IllegalArgumentException("affectedKeys 不得为负数");
      }
      Objects.requireNonNull(requestedAt, "requestedAt");
      if (redisApplied && failureCode != null) {
        throw new IllegalArgumentException("成功失效不能包含 failureCode");
      }
    }
  }

  public interface CacheCodec<T> {
    byte[] encode(T value);

    T decode(byte[] payload);
  }

  @FunctionalInterface
  public interface WaitStrategy {
    void await(Duration duration);

    static WaitStrategy threadSleep() {
      return duration -> {
        if (duration.isZero()) {
          return;
        }
        try {
          Thread.sleep(duration);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new CacheWaitInterruptedException("等待缓存加载租约时被中断", exception);
        }
      };
    }
  }

  public record DegradationPolicy(int failuresToOpen, Duration openDuration) {
    public DegradationPolicy {
      if (failuresToOpen < 1) {
        throw new IllegalArgumentException("failuresToOpen 必须大于 0");
      }
      requirePositive(openDuration, "openDuration");
    }
  }

  public static final class CacheWaitInterruptedException extends RuntimeException {
    CacheWaitInterruptedException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private record FlightKey(
      CacheKey key, long keyEpoch, Map<String, Long> tagEpochs) {}

  private record EpochSnapshot(
      CacheKey key, long keyEpoch, Set<String> tags, Map<String, Long> tagEpochs) {
    boolean isCurrent(
        ConcurrentMap<CacheKey, AtomicLong> keyEpochs,
        ConcurrentMap<String, AtomicLong> currentTagEpochs) {
      AtomicLong currentKey = keyEpochs.get(key);
      if (currentKey == null || currentKey.get() != keyEpoch) {
        return false;
      }
      for (Map.Entry<String, Long> entry : tagEpochs.entrySet()) {
        AtomicLong currentTag = currentTagEpochs.get(entry.getKey());
        if (currentTag == null || currentTag.get() != entry.getValue()) {
          return false;
        }
      }
      return true;
    }
  }

  private static final class FailureCircuit {
    private final DegradationPolicy policy;
    private int consecutiveFailures;
    private Instant openUntil;

    private FailureCircuit(DegradationPolicy policy) {
      this.policy = policy;
    }

    synchronized boolean allowRedis(Instant now) {
      if (openUntil == null) {
        return true;
      }
      if (!now.isBefore(openUntil)) {
        openUntil = null;
        consecutiveFailures = 0;
        return true;
      }
      return false;
    }

    synchronized void recordSuccess() {
      consecutiveFailures = 0;
      openUntil = null;
    }

    synchronized void recordFailure(Instant now) {
      consecutiveFailures++;
      if (consecutiveFailures >= policy.failuresToOpen()) {
        openUntil = now.plus(policy.openDuration());
      }
    }
  }

  private static void requirePositive(Duration duration, String name) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " 必须大于 0");
    }
  }
}
