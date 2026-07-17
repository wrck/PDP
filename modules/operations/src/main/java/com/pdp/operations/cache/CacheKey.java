package com.pdp.operations.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 不包含明文业务标识的稳定缓存键。
 *
 * <p>工作空间标识始终进入键空间；平台级缓存可使用 {@code null}，但业务缓存不得省略所属工作空间。
 * subject 只参与摘要，避免用户、对象或查询条件直接出现在 Redis 键和运维日志中。
 */
public record CacheKey(String namespace, UUID workspaceId, String subject) {
  private static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9.-]{1,63}$");

  public CacheKey {
    namespace = requireText(namespace, "namespace");
    subject = requireText(subject, "subject");
    if (!NAMESPACE.matcher(namespace).matches()) {
      throw new IllegalArgumentException("namespace 格式无效");
    }
    if (subject.length() > 4096) {
      throw new IllegalArgumentException("subject 长度不能超过 4096");
    }
  }

  public String redisKey() {
    String workspace = workspaceId == null ? "platform" : workspaceId.toString();
    return "pdp:cache:" + namespace + ":" + workspace + ":" + sha256(subject);
  }

  public String loadLeaseKey() {
    return redisKey() + ":load-lease";
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成缓存键摘要", exception);
    }
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
    return value.strip();
  }
}
