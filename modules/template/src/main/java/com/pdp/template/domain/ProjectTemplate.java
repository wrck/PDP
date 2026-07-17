package com.pdp.template.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 工作空间隔离的项目模板根聚合。 */
public record ProjectTemplate(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    String description,
    Status status,
    UUID currentPublishedVersionId,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    Revision revision) {
  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9.-]{2,99}");

  public enum Status {
    ACTIVE,
    ARCHIVED
  }

  public ProjectTemplate {
    Objects.requireNonNull(id, "模板 id 不能为空");
    Objects.requireNonNull(workspaceId, "模板 workspaceId 不能为空");
    Objects.requireNonNull(status, "模板状态不能为空");
    Objects.requireNonNull(createdBy, "模板创建人不能为空");
    Objects.requireNonNull(updatedBy, "模板更新人不能为空");
    Objects.requireNonNull(createdAt, "模板创建时间不能为空");
    Objects.requireNonNull(updatedAt, "模板更新时间不能为空");
    Objects.requireNonNull(revision, "模板 revision 不能为空");
    stableKey = requireText(stableKey, "模板稳定键", 100);
    if (!KEY_PATTERN.matcher(stableKey).matches()) {
      throw new IllegalArgumentException("模板稳定键格式非法: " + stableKey);
    }
    name = requireText(name, "模板名称", 200);
    description = description == null ? "" : description.trim();
    if (description.length() > 2000) {
      throw new IllegalArgumentException("模板说明不能超过 2000 个字符");
    }
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("模板更新时间不能早于创建时间");
    }
  }

  public static ProjectTemplate create(
      UUID id,
      UUID workspaceId,
      String stableKey,
      String name,
      String description,
      UUID actorId,
      Instant at) {
    return new ProjectTemplate(
        id,
        workspaceId,
        stableKey,
        name,
        description,
        Status.ACTIVE,
        null,
        actorId,
        actorId,
        at,
        at,
        new Revision(0));
  }

  public ProjectTemplate reviseDetails(
      String nextName, String nextDescription, UUID actorId, Instant at) {
    requireActive();
    return copy(nextName, nextDescription, status, currentPublishedVersionId, actorId, at);
  }

  public ProjectTemplate pointToPublishedVersion(
      ProjectTemplateVersion version, UUID actorId, Instant at) {
    requireActive();
    Objects.requireNonNull(version, "当前发布版本不能为空").requireInstantiable();
    if (!id.equals(version.templateId())) {
      throw new IllegalArgumentException("发布版本不属于当前项目模板");
    }
    return copy(name, description, status, version.id(), actorId, at);
  }

  public ProjectTemplate archive(UUID actorId, Instant at) {
    if (status == Status.ARCHIVED) {
      return this;
    }
    return copy(name, description, Status.ARCHIVED, currentPublishedVersionId, actorId, at);
  }

  public ProjectTemplate activate(UUID actorId, Instant at) {
    if (status == Status.ACTIVE) {
      return this;
    }
    return copy(name, description, Status.ACTIVE, currentPublishedVersionId, actorId, at);
  }

  private ProjectTemplate copy(
      String nextName,
      String nextDescription,
      Status nextStatus,
      UUID nextPublishedVersionId,
      UUID actorId,
      Instant at) {
    Objects.requireNonNull(actorId, "模板操作人不能为空");
    Objects.requireNonNull(at, "模板操作时间不能为空");
    if (at.isBefore(updatedAt)) {
      throw new IllegalArgumentException("模板操作时间不能早于上次更新时间");
    }
    return new ProjectTemplate(
        id,
        workspaceId,
        stableKey,
        nextName,
        nextDescription,
        nextStatus,
        nextPublishedVersionId,
        createdBy,
        actorId,
        createdAt,
        at,
        revision.next());
  }

  private void requireActive() {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("已归档模板不能修改或发布版本");
    }
  }

  private static String requireText(String value, String label, int maximumLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maximumLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maximumLength + " 个字符");
    }
    return normalized;
  }
}
