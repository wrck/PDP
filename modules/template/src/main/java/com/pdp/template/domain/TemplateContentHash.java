package com.pdp.template.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** 带算法标识的模板规范化内容哈希。 */
public record TemplateContentHash(String value) {
  private static final Pattern PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");

  public TemplateContentHash {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("模板内容哈希必须使用 sha256:<64位小写十六进制>");
    }
  }

  public static TemplateContentHash from(TemplateDefinition definition) {
    Objects.requireNonNull(definition, "模板定义不能为空");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(definition.canonicalForm().getBytes(StandardCharsets.UTF_8));
      return new TemplateContentHash("sha256:" + HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
