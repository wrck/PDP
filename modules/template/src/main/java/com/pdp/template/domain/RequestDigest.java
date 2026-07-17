package com.pdp.template.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** 用于识别同一幂等键是否携带相同业务请求的摘要。 */
public record RequestDigest(String value) {
  private static final Pattern PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");

  public RequestDigest {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("请求摘要必须使用 sha256:<64位小写十六进制>");
    }
  }

  public static RequestDigest from(
      java.util.UUID templateVersionId,
      TemplateContentHash templateContentHash,
      ProjectInstantiationInput input) {
    Objects.requireNonNull(templateVersionId, "模板版本 id 不能为空");
    Objects.requireNonNull(templateContentHash, "模板内容哈希不能为空");
    Objects.requireNonNull(input, "实例化输入不能为空");
    String canonical =
        CanonicalValue.canonical(
            java.util.Map.of(
                "templateVersionId", templateVersionId.toString(),
                "templateContentHash", templateContentHash.value(),
                "input", input.canonicalForm()));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return new RequestDigest("sha256:" + HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
