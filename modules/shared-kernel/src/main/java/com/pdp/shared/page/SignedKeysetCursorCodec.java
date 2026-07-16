package com.pdp.shared.page;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 使用 HMAC-SHA256 的可轮换 keyset 游标编解码器。 */
public final class SignedKeysetCursorCodec {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final String activeKeyId;
  private final Map<String, byte[]> keyset;
  private final Duration ttl;
  private final Clock clock;

  public SignedKeysetCursorCodec(
      String activeKeyId, Map<String, byte[]> keyset, Duration ttl, Clock clock) {
    this.activeKeyId = activeKeyId;
    this.keyset = Map.copyOf(keyset);
    this.ttl = ttl;
    this.clock = clock;
    if (!this.keyset.containsKey(activeKeyId)) {
      throw new IllegalArgumentException("活动签名键不在 keyset 中");
    }
  }

  public String encode(KeysetCursor cursor) {
    byte[] payload = serialize(cursor, activeKeyId);
    return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload, keyset.get(activeKeyId)));
  }

  public KeysetCursor decode(String encoded, String workspaceKey, String filterDigest) {
    try {
      String[] parts = encoded.split("\\.", -1);
      if (parts.length != 2) {
        throw new CursorValidationException("游标格式或签名无效");
      }
      byte[] payload = DECODER.decode(parts[0]);
      byte[] suppliedSignature = DECODER.decode(parts[1]);
      String keyId = readKeyId(payload);
      byte[] key = keyset.get(keyId);
      if (key == null || !MessageDigest.isEqual(sign(payload, key), suppliedSignature)) {
        throw new CursorValidationException("游标签名无效");
      }
      KeysetCursor cursor = deserialize(payload);
      if (!cursor.workspaceKey().equals(workspaceKey)) {
        throw new CursorValidationException("游标工作空间不匹配");
      }
      if (!cursor.filterDigest().equals(filterDigest)) {
        throw new CursorValidationException("游标筛选条件不匹配");
      }
      if (cursor.issuedAt().plus(ttl).isBefore(clock.instant())) {
        throw new CursorValidationException("游标已过期");
      }
      return cursor;
    } catch (CursorValidationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new CursorValidationException("游标格式或签名无效");
    }
  }

  private static byte[] serialize(KeysetCursor cursor, String keyId) {
    try {
      var output = new ByteArrayOutputStream();
      try (var data = new DataOutputStream(output)) {
        data.writeInt(1);
        write(data, keyId);
        write(data, cursor.workspaceKey());
        write(data, cursor.filterDigest());
        data.writeLong(cursor.issuedAt().toEpochMilli());
        data.writeInt(cursor.sortOrders().size());
        for (int i = 0; i < cursor.sortOrders().size(); i++) {
          var sort = cursor.sortOrders().get(i);
          write(data, sort.field());
          data.writeByte(sort.direction().ordinal());
          data.writeBoolean(sort.unique());
          write(data, cursor.values().get(i));
        }
      }
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("无法编码游标", exception);
    }
  }

  private static String readKeyId(byte[] payload) {
    try (var data = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (data.readInt() != 1) {
        throw new CursorValidationException("游标版本不受支持");
      }
      return read(data);
    } catch (IOException exception) {
      throw new CursorValidationException("游标格式无效");
    }
  }

  private static KeysetCursor deserialize(byte[] payload) {
    try (var data = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (data.readInt() != 1) {
        throw new CursorValidationException("游标版本不受支持");
      }
      read(data);
      String workspace = read(data);
      String filter = read(data);
      var issuedAt = java.time.Instant.ofEpochMilli(data.readLong());
      int count = data.readInt();
      if (count < 1 || count > 32) {
        throw new CursorValidationException("游标排序字段数量无效");
      }
      var sorts = new ArrayList<SortOrder>(count);
      var values = new ArrayList<String>(count);
      for (int i = 0; i < count; i++) {
        String field = read(data);
        int ordinal = data.readByte();
        if (ordinal < 0 || ordinal >= SortDirection.values().length) {
          throw new CursorValidationException("游标排序方向无效");
        }
        sorts.add(new SortOrder(field, SortDirection.values()[ordinal], data.readBoolean()));
        values.add(read(data));
      }
      if (data.available() != 0) {
        throw new CursorValidationException("游标包含未知数据");
      }
      return new KeysetCursor(workspace, filter, sorts, values, issuedAt);
    } catch (IOException exception) {
      throw new CursorValidationException("游标格式无效");
    }
  }

  private static void write(DataOutputStream data, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 8192) {
      throw new IllegalArgumentException("游标字段过长");
    }
    data.writeInt(bytes.length);
    data.write(bytes);
  }

  private static String read(DataInputStream data) throws IOException {
    int length = data.readInt();
    if (length < 0 || length > 8192) {
      throw new CursorValidationException("游标字段长度无效");
    }
    return new String(data.readNBytes(length), StandardCharsets.UTF_8);
  }

  private static byte[] sign(byte[] payload, byte[] key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("无法签名游标", exception);
    }
  }
}
