package com.pdp.shared.operation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 将确认令牌绑定到预览、操作类型、命令摘要、revision 摘要和有效期。 */
public final class SignedOperationConfirmationService implements OperationConfirmationPort {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final String activeKeyId;
  private final Map<String, byte[]> keyset;
  private final Duration ttl;
  private final Clock clock;

  public SignedOperationConfirmationService(
      String activeKeyId, Map<String, byte[]> keyset, Duration ttl, Clock clock) {
    this.activeKeyId = activeKeyId;
    this.keyset = Map.copyOf(keyset);
    this.ttl = ttl;
    this.clock = clock;
    if (!this.keyset.containsKey(activeKeyId)) {
      throw new IllegalArgumentException("活动签名键不在 keyset 中");
    }
  }

  @Override
  public String issue(OperationConfirmation confirmation) {
    Instant expiresAt = clock.instant().plus(ttl);
    String payload =
        String.join(
            "\n",
            activeKeyId,
            confirmation.previewId().toString(),
            confirmation.operationType(),
            confirmation.commandDigest(),
            confirmation.revisionDigest(),
            confirmation.previewedAt().toString(),
            expiresAt.toString());
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    return ENCODER.encodeToString(bytes)
        + "."
        + ENCODER.encodeToString(sign(bytes, keyset.get(activeKeyId)));
  }

  @Override
  public OperationConfirmation verify(String token, OperationConfirmation expected) {
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 2) {
        throw new OperationConfirmationException("确认令牌格式无效");
      }
      byte[] payloadBytes = DECODER.decode(parts[0]);
      String[] values = new String(payloadBytes, StandardCharsets.UTF_8).split("\\n", -1);
      if (values.length != 7) {
        throw new OperationConfirmationException("确认令牌格式无效");
      }
      byte[] key = keyset.get(values[0]);
      if (key == null
          || !MessageDigest.isEqual(sign(payloadBytes, key), DECODER.decode(parts[1]))) {
        throw new OperationConfirmationException("确认令牌签名无效");
      }
      var actual =
          new OperationConfirmation(
              UUID.fromString(values[1]),
              values[2],
              values[3],
              values[4],
              Instant.parse(values[5]));
      if (Instant.parse(values[6]).isBefore(clock.instant())) {
        throw new OperationConfirmationException("确认令牌已过期");
      }
      if (!actual.equals(expected)) {
        throw new OperationConfirmationException("确认令牌与当前预览或版本不匹配");
      }
      return actual;
    } catch (OperationConfirmationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new OperationConfirmationException("确认令牌格式无效");
    }
  }

  private static byte[] sign(byte[] payload, byte[] key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("无法签名确认令牌", exception);
    }
  }
}
