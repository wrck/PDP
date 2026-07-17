package com.pdp.template.domain;

import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.context.IdempotencyKey;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 与项目及所有默认计划同事务写入的已完成实例化证据。 */
public record ProjectInstantiationRecord(
    UUID id,
    UUID workspaceId,
    UUID projectId,
    ProjectCreationMode creationMode,
    UUID templateVersionId,
    TemplateContentHash templateContentHash,
    UUID domainPackageSnapshotId,
    IdempotencyKey idempotencyKey,
    RequestDigest requestDigest,
    ProjectInstantiationSummary generatedSummary,
    List<GeneratedProjectObjectRef> generatedObjects,
    Status status,
    Instant completedAt,
    Revision revision) {

  public enum Status {
    COMPLETED
  }

  public ProjectInstantiationRecord {
    Objects.requireNonNull(id, "实例化记录 id 不能为空");
    Objects.requireNonNull(workspaceId, "实例化记录 workspaceId 不能为空");
    Objects.requireNonNull(projectId, "实例化记录 projectId 不能为空");
    Objects.requireNonNull(creationMode, "项目创建模式不能为空");
    Objects.requireNonNull(idempotencyKey, "实例化记录幂等键不能为空");
    Objects.requireNonNull(requestDigest, "实例化记录请求摘要不能为空");
    Objects.requireNonNull(generatedSummary, "实例化记录汇总不能为空");
    generatedObjects = List.copyOf(generatedObjects);
    Objects.requireNonNull(status, "实例化记录状态不能为空");
    Objects.requireNonNull(completedAt, "实例化完成时间不能为空");
    Objects.requireNonNull(revision, "实例化记录 revision 不能为空");
    validateCreationMode(
        creationMode, templateVersionId, templateContentHash, domainPackageSnapshotId);
    validateGeneratedObjects(generatedSummary, generatedObjects);
  }

  public static ProjectInstantiationRecord completedFromTemplate(
      UUID id,
      UUID projectId,
      UUID domainPackageSnapshotId,
      IdempotencyKey idempotencyKey,
      ProjectInstantiationPlan plan,
      List<GeneratedProjectObjectRef> generatedObjects,
      Instant completedAt) {
    Objects.requireNonNull(plan, "实例化计划不能为空").requireCreatable();
    requireMatchesPlan(plan, generatedObjects);
    return new ProjectInstantiationRecord(
        id,
        plan.workspaceId(),
        projectId,
        ProjectCreationMode.TEMPLATE,
        plan.templateVersionId(),
        plan.templateContentHash(),
        domainPackageSnapshotId,
        idempotencyKey,
        plan.requestDigest(),
        ProjectInstantiationSummary.fromGeneratedObjects(generatedObjects),
        generatedObjects,
        Status.COMPLETED,
        completedAt,
        new Revision(0));
  }

  public static ProjectInstantiationRecord completedBlank(
      UUID id,
      UUID workspaceId,
      UUID projectId,
      UUID domainPackageSnapshotId,
      IdempotencyKey idempotencyKey,
      RequestDigest requestDigest,
      List<GeneratedProjectObjectRef> generatedObjects,
      Instant completedAt) {
    return new ProjectInstantiationRecord(
        id,
        workspaceId,
        projectId,
        ProjectCreationMode.BLANK,
        null,
        null,
        domainPackageSnapshotId,
        idempotencyKey,
        requestDigest,
        ProjectInstantiationSummary.fromGeneratedObjects(generatedObjects),
        generatedObjects,
        Status.COMPLETED,
        completedAt,
        new Revision(0));
  }

  public void requireSameRequest(RequestDigest candidate) {
    if (!requestDigest.equals(candidate)) {
      throw new IdempotencyConflictException(idempotencyKey);
    }
  }

  private static void validateCreationMode(
      ProjectCreationMode mode,
      UUID templateVersionId,
      TemplateContentHash templateContentHash,
      UUID domainPackageSnapshotId) {
    if (mode == ProjectCreationMode.TEMPLATE
        && (templateVersionId == null
            || templateContentHash == null
            || domainPackageSnapshotId == null)) {
      throw new IllegalArgumentException("模板创建必须保存模板版本、内容哈希和领域包快照");
    }
    if (mode == ProjectCreationMode.BLANK
        && (templateVersionId != null || templateContentHash != null)) {
      throw new IllegalArgumentException("空白创建不能保存模板版本或模板内容哈希");
    }
  }

  private static void validateGeneratedObjects(
      ProjectInstantiationSummary summary, List<GeneratedProjectObjectRef> generatedObjects) {
    Set<UUID> objectIds = new HashSet<>();
    Set<String> componentKeys = new HashSet<>();
    for (GeneratedProjectObjectRef generatedObject : generatedObjects) {
      Objects.requireNonNull(generatedObject, "生成对象引用不能为空");
      if (!objectIds.add(generatedObject.objectId())) {
        throw new IllegalArgumentException("生成对象 id 重复: " + generatedObject.objectId());
      }
      if (!componentKeys.add(generatedObject.componentKey())) {
        throw new IllegalArgumentException(
            "生成对象 componentKey 重复: " + generatedObject.componentKey());
      }
    }
    if (!summary.equals(ProjectInstantiationSummary.fromGeneratedObjects(generatedObjects))) {
      throw new IllegalArgumentException("实例化汇总与生成对象引用不一致");
    }
  }

  private static void requireMatchesPlan(
      ProjectInstantiationPlan plan, List<GeneratedProjectObjectRef> generatedObjects) {
    Set<String> expected = new HashSet<>();
    for (ProjectObjectBlueprint blueprint : plan.generatedObjects()) {
      expected.add(
          blueprint.componentKey() + "|" + blueprint.objectType() + "|" + blueprint.objectId());
    }
    Set<String> actual = new HashSet<>();
    for (GeneratedProjectObjectRef generatedObject : generatedObjects) {
      actual.add(
          generatedObject.componentKey()
              + "|"
              + generatedObject.objectType()
              + "|"
              + generatedObject.objectId());
    }
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException("实例化完成对象与已批准计划不一致");
    }
  }
}
