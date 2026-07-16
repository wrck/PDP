package com.pdp.shared.id;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    @Test
    void generatesVersion7Uuid() {
        UUID id = UuidV7Generator.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @RepeatedTest(20)
    void generatesUniqueUuids() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7Generator.next());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void isMonotonicWithinSameMillisecond() {
        Instant now = Instant.now();
        UUID a = UuidV7Generator.generate(now);
        UUID b = UuidV7Generator.generate(now);
        // 同毫秒内计数器递增，MSB 应递增
        assertThat(b.getMostSignificantBits()).isGreaterThan(a.getMostSignificantBits());
    }

    @Test
    void detectsNonV7Uuid() {
        assertThat(UuidV7Generator.isUuidV7(UUID.randomUUID())).isFalse();
        assertThat(UuidV7Generator.isUuidV7(UuidV7Generator.next())).isTrue();
        assertThat(UuidV7Generator.isUuidV7(null)).isFalse();
    }
}
