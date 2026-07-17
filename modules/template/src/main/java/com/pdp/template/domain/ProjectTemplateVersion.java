package com.pdp.template.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 项目模板的不可覆盖版本聚合。 */
public record ProjectTemplateVersion(
    UUID id,
    UUID templateId,
    SemanticVersion semanticVersion,
    UUID baseVersionId,
    UUID domainPackageVersionId,
    Status status,
    TemplateContentHash contentHash,
    TemplateDefinition definition,
    String changeSummary,
    UUID createdBy,
    UUID frozenBy,
    Instant frozenAt,
    UUID publishedBy,
    Instant publishedAt,
    UUID retiredBy,
    Instant retiredAt,
    Instant createdAt,
    Instant updatedAt,
    Revision revision) {

  public enum Status {
    DRAFT,
    FROZEN,
    PUBLISHED,
    RETIRED
  }

  public ProjectTemplateVersion {
    Objects.requireNonNull(id, "模板版本 id 不能为空");
    Objects.requireNonNull(templateId, "模板版本 templateId 不能为空");
    Objects.requireNonNull(semanticVersion, "模板语义版本不能为空");
    Objects.requireNonNull(domainPackageVersionId, "领域包版本不能为空");
    Objects.requireNonNull(status, "模板版本状态不能为空");
    Objects.requireNonNull(definition, "模板版本定义不能为空");
    Objects.requireNonNull(createdBy, "模板版本创建人不能为空");
    Objects.requireNonNull(createdAt, "模板版本创建时间不能为空");
    Objects.requireNonNull(updatedAt, "模板版本更新时间不能为空");
    Objects.requireNonNull(revision, "模板版本 revision 不能为空");
    if (id.equals(baseVersionId)) {
      throw new IllegalArgumentException("模板版本不能以自身作为基础版本");
    }
    changeSummary = requireText(changeSummary, "模板版本变更说明", 1000);
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("模板版本更新时间不能早于创建时间");
    }
    validateLifecycleEvidence(
        status,
        contentHash,
        definition,
        frozenBy,
        frozenAt,
        publishedBy,
        publishedAt,
        retiredBy,
        retiredAt);
  }

  public static ProjectTemplateVersion draft(
      UUID id,
      UUID templateId,
      SemanticVersion semanticVersion,
      UUID baseVersionId,
      UUID domainPackageVersionId,
      TemplateDefinition definition,
      String changeSummary,
      UUID actorId,
      Instant at) {
    return new ProjectTemplateVersion(
        id,
        templateId,
        semanticVersion,
        baseVersionId,
        domainPackageVersionId,
        Status.DRAFT,
        null,
        definition,
        changeSummary,
        actorId,
        null,
        null,
        null,
        null,
        null,
        null,
        at,
        at,
        new Revision(0));
  }

  public ProjectTemplateVersion revise(
      UUID nextDomainPackageVersionId,
      TemplateDefinition nextDefinition,
      String nextChangeSummary,
      Instant at) {
    requireStatus(Status.DRAFT, "仅草稿模板版本可以编辑");
    return new ProjectTemplateVersion(
        id,
        templateId,
        semanticVersion,
        baseVersionId,
        nextDomainPackageVersionId,
        status,
        null,
        nextDefinition,
        nextChangeSummary,
        createdBy,
        null,
        null,
        null,
        null,
        null,
        null,
        createdAt,
        requireOperationTime(at),
        revision.next());
  }

  public ProjectTemplateVersion freeze(UUID actorId, Instant at) {
    requireStatus(Status.DRAFT, "仅草稿模板版本可以冻结");
    Instant operationTime = requireOperationTime(at);
    return new ProjectTemplateVersion(
        id,
        templateId,
        semanticVersion,
        baseVersionId,
        domainPackageVersionId,
        Status.FROZEN,
        TemplateContentHash.from(definition),
        definition,
        changeSummary,
        createdBy,
        Objects.requireNonNull(actorId, "冻结人不能为空"),
        operationTime,
        null,
        null,
        null,
        null,
        createdAt,
        operationTime,
        revision.next());
  }

  public ProjectTemplateVersion publish(UUID actorId, Instant at) {
    requireStatus(Status.FROZEN, "仅已冻结模板版本可以发布");
    Instant operationTime = requireOperationTime(at);
    return new ProjectTemplateVersion(
        id,
        templateId,
        semanticVersion,
        baseVersionId,
        domainPackageVersionId,
        Status.PUBLISHED,
        contentHash,
        definition,
        changeSummary,
        createdBy,
        frozenBy,
        frozenAt,
        Objects.requireNonNull(actorId, "发布人不能为空"),
        operationTime,
        null,
        null,
        createdAt,
        operationTime,
        revision.next());
  }

  public ProjectTemplateVersion retire(UUID actorId, Instant at) {
    requireStatus(Status.PUBLISHED, "仅已发布模板版本可以退役");
    Instant operationTime = requireOperationTime(at);
    return new ProjectTemplateVersion(
        id,
        templateId,
        semanticVersion,
        baseVersionId,
        domainPackageVersionId,
        Status.RETIRED,
        contentHash,
        definition,
        changeSummary,
        createdBy,
        frozenBy,
        frozenAt,
        publishedBy,
        publishedAt,
        Objects.requireNonNull(actorId, "退役人不能为空"),
        operationTime,
        createdAt,
        operationTime,
        revision.next());
  }

  public void requireInstantiable() {
    requireStatus(Status.PUBLISHED, "只有已发布且未退役的模板版本可以实例化项目");
  }

  private Instant requireOperationTime(Instant at) {
    Objects.requireNonNull(at, "模板版本操作时间不能为空");
    if (at.isBefore(updatedAt)) {
      throw new IllegalArgumentException("模板版本操作时间不能早于上次更新时间");
    }
    return at;
  }

  private void requireStatus(Status required, String message) {
    if (status != required) {
      throw new IllegalStateException(message + ": " + status);
    }
  }

  private static void validateLifecycleEvidence(
      Status status,
      TemplateContentHash contentHash,
      TemplateDefinition definition,
      UUID frozenBy,
      Instant frozenAt,
      UUID publishedBy,
      Instant publishedAt,
      UUID retiredBy,
      Instant retiredAt) {
    boolean frozen = frozenBy != null && frozenAt != null;
    boolean published = publishedBy != null && publishedAt != null;
    boolean retired = retiredBy != null && retiredAt != null;
    if ((frozenBy == null) != (frozenAt == null)
        || (publishedBy == null) != (publishedAt == null)
        || (retiredBy == null) != (retiredAt == null)) {
      throw new IllegalArgumentException("模板版本生命周期操作人与时间必须成对出现");
    }
    switch (status) {
      case DRAFT -> {
        if (contentHash != null || frozen || published || retired) {
          throw new IllegalArgumentException("草稿模板版本不能包含冻结、发布或退役证据");
        }
      }
      case FROZEN -> {
        if (contentHash == null || !frozen || published || retired) {
          throw new IllegalArgumentException("冻结模板版本的生命周期证据不完整");
        }
      }
      case PUBLISHED -> {
        if (contentHash == null || !frozen || !published || retired) {
          throw new IllegalArgumentException("发布模板版本的生命周期证据不完整");
        }
      }
      case RETIRED -> {
        if (contentHash == null || !frozen || !published || !retired) {
          throw new IllegalArgumentException("退役模板版本的生命周期证据不完整");
        }
      }
    }
    if (contentHash != null && !contentHash.equals(TemplateContentHash.from(definition))) {
      throw new IllegalArgumentException("模板版本内容与冻结哈希不一致");
    }
    if ((publishedAt != null && publishedAt.isBefore(frozenAt))
        || (retiredAt != null && retiredAt.isBefore(publishedAt))) {
      throw new IllegalArgumentException("模板版本生命周期时间顺序非法");
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
