package com.pdp.shared.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignedKeysetCursorCodecTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void 应签名并恢复稳定排序游标() {
    var codec =
        new SignedKeysetCursorCodec(
            "cursor-2026-01",
            Map.of("cursor-2026-01", "a-32-byte-cursor-secret-key-value".getBytes(StandardCharsets.UTF_8)),
            Duration.ofHours(1),
            CLOCK);
    var cursor =
        new KeysetCursor(
            "workspace-1",
            "filter-digest",
            List.of(
                new SortOrder("updatedAt", SortDirection.DESCENDING, false),
                new SortOrder("id", SortDirection.ASCENDING, true)),
            List.of("2026-07-17T07:59:00Z", "018ff3d0"),
            CLOCK.instant());

    var encoded = codec.encode(cursor);
    var decoded = codec.decode(encoded, "workspace-1", "filter-digest");

    assertThat(decoded).isEqualTo(cursor);
  }

  @Test
  void 应拒绝篡改和筛选条件复用() {
    var codec =
        new SignedKeysetCursorCodec(
            "k1",
            Map.of("k1", "another-32-byte-cursor-secret-value".getBytes(StandardCharsets.UTF_8)),
            Duration.ofHours(1),
            CLOCK);
    var cursor =
        new KeysetCursor(
            "workspace-1",
            "filter-a",
            List.of(new SortOrder("id", SortDirection.ASCENDING, true)),
            List.of("42"),
            CLOCK.instant());
    var encoded = codec.encode(cursor);
    var tampered = encoded.substring(0, encoded.length() - 1) + (encoded.endsWith("A") ? "B" : "A");

    assertThatThrownBy(() -> codec.decode(tampered, "workspace-1", "filter-a"))
        .isInstanceOf(CursorValidationException.class)
        .hasMessageContaining("签名");
    assertThatThrownBy(() -> codec.decode(encoded, "workspace-1", "filter-b"))
        .isInstanceOf(CursorValidationException.class)
        .hasMessageContaining("筛选");
  }

  @Test
  void 稳定排序必须以唯一字段收尾() {
    assertThatThrownBy(
            () ->
                new KeysetCursor(
                    "workspace-1",
                    "filter-a",
                    List.of(new SortOrder("updatedAt", SortDirection.DESCENDING, false)),
                    List.of("2026-07-17T07:59:00Z"),
                    CLOCK.instant()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("唯一");
  }
}
