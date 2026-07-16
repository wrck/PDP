package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workspace(
    UUID id,
    String code,
    String name,
    UUID ownerUserId,
    Status status,
    String defaultLocale,
    String defaultTimezone,
    UUID dataClassificationPolicyId,
    Revision revision,
    Instant createdAt,
    Instant updatedAt) {

  public enum Status {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    ARCHIVED
  }

  public Workspace {
    Objects.requireNonNull(id);
    code = requireText(code, "工作空间编码");
    name = requireText(name, "工作空间名称");
    Objects.requireNonNull(ownerUserId);
    Objects.requireNonNull(status);
    defaultLocale = requireText(defaultLocale, "默认语言");
    defaultTimezone = requireText(defaultTimezone, "默认时区");
    Objects.requireNonNull(revision);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(updatedAt);
  }

  public static Workspace draft(
      UUID id,
      String code,
      String name,
      UUID ownerUserId,
      String locale,
      String timezone,
      Instant at) {
    return new Workspace(
        id, code, name, ownerUserId, Status.DRAFT, locale, timezone, null, new Revision(0), at, at);
  }

  public Workspace activate(Instant at) {
    if (status != Status.DRAFT && status != Status.SUSPENDED) {
      throw new IllegalStateException("仅草稿或暂停工作空间可以启用");
    }
    return transition(Status.ACTIVE, at);
  }

  public Workspace suspend(Instant at) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("仅活动工作空间可以暂停");
    }
    return transition(Status.SUSPENDED, at);
  }

  public Workspace archive(Instant at) {
    if (status == Status.ARCHIVED) {
      return this;
    }
    if (status != Status.SUSPENDED) {
      throw new IllegalStateException("工作空间必须先暂停再归档");
    }
    return transition(Status.ARCHIVED, at);
  }

  private Workspace transition(Status next, Instant at) {
    return new Workspace(
        id,
        code,
        name,
        ownerUserId,
        next,
        defaultLocale,
        defaultTimezone,
        dataClassificationPolicyId,
        revision.next(),
        createdAt,
        at);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    return value;
  }
}
