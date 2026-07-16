package com.pdp.shared.concurrency;

import java.util.Objects;

/** revision 的强 ETag 表达。 */
public record EntityTag(String value, Revision revision) {
  public EntityTag {
    Objects.requireNonNull(value, "ETag 不能为空");
    Objects.requireNonNull(revision, "revision 不能为空");
  }

  public static EntityTag from(Revision revision) {
    return new EntityTag("\"" + revision.value() + "\"", revision);
  }

  public static EntityTag parse(String value) {
    if (value == null || value.length() < 3 || !value.startsWith("\"") || !value.endsWith("\"")) {
      throw new IllegalArgumentException("仅支持 revision 强 ETag");
    }
    try {
      return from(new Revision(Long.parseLong(value.substring(1, value.length() - 1))));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("ETag 中的 revision 无效", exception);
    }
  }
}
