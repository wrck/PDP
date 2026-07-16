package com.pdp.shared.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用生成的 UUIDv7（RFC 9562）。
 *
 * <p>宪章与持久化设计要求：所有业务记录使用由应用生成的 UUIDv7 主键，
 * 不调用数据库生成函数。UUIDv7 时间有序，利于 B+Tree 聚簇插入与 keyset 分页兜底。
 *
 * <p>结构：48 位毫秒时间戳 | 4 位版本(0111) | 12 位随机 | 2 位变体(10) | 62 位随机。
 * 同一毫秒内使用单调计数器保证有序递增。
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private UuidV7Generator() {
    }

    public static UUID next() {
        return generate(Instant.now());
    }

    static UUID generate(Instant now) {
        long timestampMs = now.toEpochMilli();

        // rand_a（12 位）：每毫秒单调递增计数器取低 12 位，保证同毫秒内有序
        int counter = COUNTER.incrementAndGet() & 0x0FFF;
        long randA = counter;

        // rand_b（62 位）：安全随机
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;

        long msb = (timestampMs << 16)
                | (0x7L << 12)          // version 7
                | (randA & 0x0FFF);

        long lsb = (0x2L << 62)         // variant 10
                | (randB & 0x3FFFFFFFFFFFFFFFL);

        return new UUID(msb, lsb);
    }

    /** 校验给定 UUID 是否为版本 7。 */
    public static boolean isUuidV7(UUID id) {
        return id != null && id.version() == 7;
    }
}
