package com.pdp.shared.page;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    private CursorCodec codec;
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes();

    @BeforeEach
    void setUp() {
        codec = new CursorCodec(SECRET);
    }

    @Test
    void encodesAndDecodesRoundTrip() {
        CursorPayload payload = CursorPayload.after(
                "project.list",
                List.of("2026-07-17T00:00:00Z", "Alpha"),
                List.of(false, false),
                UUID.randomUUID(),
                SortDirection.ASC,
                "filter:v1",
                "scope:w1",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS));

        String cursor = codec.encode(payload);
        assertThat(cursor).isNotBlank();

        CursorPayload decoded = codec.decode(cursor, "project.list", "filter:v1", "scope:w1");
        assertThat(decoded.queryType()).isEqualTo("project.list");
        assertThat(decoded.direction()).isEqualTo(SortDirection.ASC);
        assertThat(decoded.lastSortValues()).containsExactly("2026-07-17T00:00:00Z", "Alpha");
    }

    @Test
    void rejectsTamperedCursor() {
        CursorPayload payload = samplePayload();
        String cursor = codec.encode(payload);
        String tampered = cursor.substring(0, cursor.length() - 2) + "xx";
        assertThatThrownBy(() -> codec.decode(tampered, "project.list", "filter:v1", "scope:w1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQueryTypeMismatch() {
        CursorPayload payload = samplePayload();
        String cursor = codec.encode(payload);
        assertThatThrownBy(() -> codec.decode(cursor, "different.query", "filter:v1", "scope:w1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("查询类型不匹配");
    }

    @Test
    void rejectsFilterDigestMismatch() {
        CursorPayload payload = samplePayload();
        String cursor = codec.encode(payload);
        assertThatThrownBy(() -> codec.decode(cursor, "project.list", "filter:v2", "scope:w1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过滤条件已变化");
    }

    @Test
    void rejectsExpiredCursor() {
        CursorPayload payload = CursorPayload.after(
                "project.list", List.of("a"), List.of(false), UUID.randomUUID(),
                SortDirection.ASC, "filter:v1", "scope:w1",
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        String cursor = codec.encode(payload);
        assertThatThrownBy(() -> codec.decode(cursor, "project.list", "filter:v1", "scope:w1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new CursorCodec("short".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("noseparator", "project.list", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("", "project.list", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CursorPayload samplePayload() {
        return CursorPayload.after(
                "project.list",
                List.of("2026-07-17T00:00:00Z"),
                List.of(false),
                UUID.randomUUID(),
                SortDirection.ASC,
                "filter:v1",
                "scope:w1",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
