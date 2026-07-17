package com.pdp.operations.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.operations.cache.RedisCacheStore.CacheEntry;
import com.pdp.operations.cache.RedisCacheStore.RedisCacheException;
import com.pdp.operations.cache.ResilientRedisCache.CacheClass;
import com.pdp.operations.cache.ResilientRedisCache.CacheCodec;
import com.pdp.operations.cache.ResilientRedisCache.CachePolicy;
import com.pdp.operations.cache.ResilientRedisCache.DegradationPolicy;
import com.pdp.operations.cache.ResilientRedisCache.Source;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientRedisCacheTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
  private static final CacheCodec<String> STRING_CODEC =
      new CacheCodec<>() {
        @Override
        public byte[] encode(String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] payload) {
          return new String(payload, StandardCharsets.UTF_8);
        }
      };
  private static final CachePolicy REBUILDABLE_POLICY =
      new CachePolicy(
          CacheClass.REBUILDABLE,
          Duration.ofMinutes(1),
          Duration.ofSeconds(5),
          Duration.ZERO,
          1);

  @Test
  void 缓存命中后相同请求只回源一次() {
    FakeRedisStore store = new FakeRedisStore(CLOCK);
    ResilientRedisCache cache = cache(store);
    CacheKey key = key("project:1");
    AtomicInteger loads = new AtomicInteger();

    var first =
        cache.getOrLoad(
            key,
            REBUILDABLE_POLICY,
            Set.of("project:1"),
            STRING_CODEC,
            () -> "value-" + loads.incrementAndGet());
    var second =
        cache.getOrLoad(
            key,
            REBUILDABLE_POLICY,
            Set.of("project:1"),
            STRING_CODEC,
            () -> "value-" + loads.incrementAndGet());

    assertThat(first.source()).isEqualTo(Source.LOADER);
    assertThat(first.cacheAvailable()).isTrue();
    assertThat(second.source()).isEqualTo(Source.CACHE);
    assertThat(second.value()).isEqualTo("value-1");
    assertThat(loads).hasValue(1);
  }

  @Test
  void 标签失效后必须删除旧值并重新回源() {
    FakeRedisStore store = new FakeRedisStore(CLOCK);
    ResilientRedisCache cache = cache(store);
    CacheKey key = key("project:2");
    AtomicInteger loads = new AtomicInteger();

    cache.getOrLoad(
        key,
        REBUILDABLE_POLICY,
        Set.of("workspace-role:7"),
        STRING_CODEC,
        () -> "value-" + loads.incrementAndGet());
    var invalidation = cache.invalidateTag("workspace-role:7");
    var reloaded =
        cache.getOrLoad(
            key,
            REBUILDABLE_POLICY,
            Set.of("workspace-role:7"),
            STRING_CODEC,
            () -> "value-" + loads.incrementAndGet());

    assertThat(invalidation.redisApplied()).isTrue();
    assertThat(invalidation.affectedKeys()).isEqualTo(1);
    assertThat(reloaded.source()).isEqualTo(Source.LOADER);
    assertThat(reloaded.value()).isEqualTo("value-2");
    assertThat(loads).hasValue(2);
  }

  @Test
  void 授权缓存TTL超过五秒必须拒绝() {
    assertThatThrownBy(
            () ->
                new CachePolicy(
                    CacheClass.AUTHORIZATION,
                    Duration.ofSeconds(6),
                    Duration.ofSeconds(2),
                    Duration.ZERO,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5 秒");
  }

  @Test
  void Redis故障时必须降级回源且不阻断核心读取() {
    FakeRedisStore store = new FakeRedisStore(CLOCK);
    store.unavailable.set(true);
    ResilientRedisCache cache =
        new ResilientRedisCache(
            store,
            CLOCK,
            duration -> {},
            new DegradationPolicy(1, Duration.ofSeconds(5)));
    AtomicInteger loads = new AtomicInteger();

    var degraded =
        cache.getOrLoad(
            key("project:3"),
            REBUILDABLE_POLICY,
            Set.of("project:3"),
            STRING_CODEC,
            () -> {
              loads.incrementAndGet();
              return "database-value";
            });

    assertThat(degraded.source()).isEqualTo(Source.DEGRADED_LOADER);
    assertThat(degraded.value()).isEqualTo("database-value");
    assertThat(degraded.cacheAvailable()).isFalse();
    assertThat(loads).hasValue(1);
  }

  @Test
  void 同键并发请求必须通过SingleFlight合并回源() throws Exception {
    FakeRedisStore store = new FakeRedisStore(CLOCK);
    ResilientRedisCache cache = cache(store);
    CacheKey key = key("task:1");
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch callersReady = new CountDownLatch(8);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch loaderStarted = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    List<Future<String>> futures = new ArrayList<>();

    try {
      for (int index = 0; index < 8; index++) {
        futures.add(
            executor.submit(
                () -> {
                  callersReady.countDown();
                  assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                  return cache
                      .getOrLoad(
                          key,
                          REBUILDABLE_POLICY,
                          Set.of("task:1"),
                          STRING_CODEC,
                          () -> {
                            loads.incrementAndGet();
                            loaderStarted.countDown();
                            await(releaseLoader);
                            return "shared-value";
                          })
                      .value();
                }));
      }
      assertThat(callersReady.await(2, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(loaderStarted.await(2, TimeUnit.SECONDS)).isTrue();
      releaseLoader.countDown();

      for (Future<String> future : futures) {
        assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("shared-value");
      }
      assertThat(loads).hasValue(1);
    } finally {
      releaseLoader.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void 失效期间完成的旧回源值不得重新写回() throws Exception {
    FakeRedisStore store = new FakeRedisStore(CLOCK);
    ResilientRedisCache cache = cache(store);
    CacheKey key = key("approval:1");
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch firstLoadStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstLoad = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      Future<String> future =
          executor.submit(
              () ->
                  cache
                      .getOrLoad(
                          key,
                          REBUILDABLE_POLICY,
                          Set.of("approval:1"),
                          STRING_CODEC,
                          () -> {
                            int sequence = loads.incrementAndGet();
                            if (sequence == 1) {
                              firstLoadStarted.countDown();
                              await(releaseFirstLoad);
                            }
                            return "value-" + sequence;
                          })
                      .value());

      assertThat(firstLoadStarted.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(cache.invalidate(key).redisApplied()).isTrue();
      releaseFirstLoad.countDown();

      assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("value-2");
      assertThat(loads).hasValue(2);
      assertThat(store.decodedValue(key)).contains("value-2");
      assertThat(store.putAttempts).hasValue(1);
    } finally {
      releaseFirstLoad.countDown();
      executor.shutdownNow();
    }
  }

  private static ResilientRedisCache cache(FakeRedisStore store) {
    return new ResilientRedisCache(
        store,
        CLOCK,
        duration -> {},
        new DegradationPolicy(3, Duration.ofSeconds(5)));
  }

  private static CacheKey key(String subject) {
    return new CacheKey("test-view", UUID.randomUUID(), subject);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(2, TimeUnit.SECONDS)) {
        throw new IllegalStateException("测试等待超时");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("测试等待被中断", exception);
    }
  }

  private static final class FakeRedisStore implements RedisCacheStore {
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> values = new HashMap<>();
    private final Map<CacheKey, Long> generations = new HashMap<>();
    private final Map<String, Set<CacheKey>> taggedKeys = new HashMap<>();
    private final Map<CacheKey, String> leases = new HashMap<>();
    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final AtomicInteger putAttempts = new AtomicInteger();

    private FakeRedisStore(Clock clock) {
      this.clock = clock;
    }

    @Override
    public synchronized Optional<CacheEntry> get(CacheKey key) {
      requireAvailable();
      return Optional.ofNullable(values.get(key));
    }

    @Override
    public synchronized long generation(CacheKey key) {
      requireAvailable();
      return generations.getOrDefault(key, 0L);
    }

    @Override
    public synchronized boolean putIfGeneration(
        CacheKey key,
        long expectedGeneration,
        byte[] payload,
        Duration ttl,
        Set<String> invalidationTags) {
      requireAvailable();
      putAttempts.incrementAndGet();
      if (generation(key) != expectedGeneration) {
        return false;
      }
      values.put(
          key, new CacheEntry(payload, expectedGeneration, clock.instant().plus(ttl)));
      for (String tag : invalidationTags) {
        taggedKeys.computeIfAbsent(tag, ignored -> new HashSet<>()).add(key);
      }
      return true;
    }

    @Override
    public synchronized long invalidate(CacheKey key) {
      requireAvailable();
      generations.put(key, generation(key) + 1);
      return values.remove(key) == null ? 0 : 1;
    }

    @Override
    public synchronized long invalidateTag(String tag) {
      requireAvailable();
      long affected = 0;
      for (CacheKey key : new HashSet<>(taggedKeys.getOrDefault(tag, Set.of()))) {
        generations.put(key, generation(key) + 1);
        if (values.remove(key) != null) {
          affected++;
        }
      }
      taggedKeys.remove(tag);
      return affected;
    }

    @Override
    public synchronized boolean tryAcquireLoadLease(
        CacheKey key, String ownerToken, Duration ttl) {
      requireAvailable();
      return leases.putIfAbsent(key, ownerToken) == null;
    }

    @Override
    public synchronized void releaseLoadLease(CacheKey key, String ownerToken) {
      requireAvailable();
      leases.remove(key, ownerToken);
    }

    synchronized Optional<String> decodedValue(CacheKey key) {
      return Optional.ofNullable(values.get(key))
          .map(CacheEntry::payload)
          .map(payload -> new String(payload, StandardCharsets.UTF_8));
    }

    private void requireAvailable() {
      if (unavailable.get()) {
        throw new RedisCacheException("Redis 不可用");
      }
    }
  }
}
