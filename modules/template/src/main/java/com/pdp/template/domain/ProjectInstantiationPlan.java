package com.pdp.template.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 绑定发布模板内容哈希的不可变项目实例化预览。 */
public final class ProjectInstantiationPlan {
  private final UUID planId;
  private final UUID workspaceId;
  private final UUID templateId;
  private final UUID templateVersionId;
  private final TemplateContentHash templateContentHash;
  private final UUID domainPackageVersionId;
  private final ProjectInstantiationInput input;
  private final RequestDigest requestDigest;
  private final List<ProjectObjectBlueprint> generatedObjects;
  private final ProjectInstantiationSummary generatedSummary;
  private final List<ProjectInstantiationIssue> validationIssues;
  private final Instant plannedAt;

  private ProjectInstantiationPlan(
      UUID planId,
      UUID workspaceId,
      ProjectTemplateVersion version,
      ProjectInstantiationInput input,
      RequestDigest requestDigest,
      List<ProjectObjectBlueprint> generatedObjects,
      List<ProjectInstantiationIssue> validationIssues,
      Instant plannedAt) {
    this.planId = Objects.requireNonNull(planId, "实例化计划 id 不能为空");
    this.workspaceId = Objects.requireNonNull(workspaceId, "实例化计划 workspaceId 不能为空");
    this.templateId = version.templateId();
    this.templateVersionId = version.id();
    this.templateContentHash = version.contentHash();
    this.domainPackageVersionId = version.domainPackageVersionId();
    this.input = Objects.requireNonNull(input, "实例化输入不能为空");
    this.requestDigest = Objects.requireNonNull(requestDigest, "请求摘要不能为空");
    this.generatedObjects = List.copyOf(generatedObjects);
    this.generatedSummary = ProjectInstantiationSummary.fromBlueprints(this.generatedObjects);
    this.validationIssues = List.copyOf(validationIssues);
    this.plannedAt = Objects.requireNonNull(plannedAt, "实例化计划时间不能为空");
    validateBlueprints(version.definition(), this.generatedObjects, creatable());
  }

  public static ProjectInstantiationPlan create(
      UUID planId,
      UUID workspaceId,
      ProjectTemplateVersion version,
      ProjectInstantiationInput input,
      List<ProjectObjectBlueprint> generatedObjects,
      List<ProjectInstantiationIssue> validationIssues,
      Instant plannedAt) {
    Objects.requireNonNull(version, "模板版本不能为空").requireInstantiable();
    Objects.requireNonNull(input, "实例化输入不能为空").requireDeclaredKeys(version.definition());
    RequestDigest requestDigest =
        RequestDigest.from(version.id(), version.contentHash(), input);
    return new ProjectInstantiationPlan(
        planId,
        workspaceId,
        version,
        input,
        requestDigest,
        generatedObjects,
        validationIssues,
        plannedAt);
  }

  public UUID planId() {
    return planId;
  }

  public UUID workspaceId() {
    return workspaceId;
  }

  public UUID templateId() {
    return templateId;
  }

  public UUID templateVersionId() {
    return templateVersionId;
  }

  public TemplateContentHash templateContentHash() {
    return templateContentHash;
  }

  public UUID domainPackageVersionId() {
    return domainPackageVersionId;
  }

  public ProjectInstantiationInput input() {
    return input;
  }

  public RequestDigest requestDigest() {
    return requestDigest;
  }

  public List<ProjectObjectBlueprint> generatedObjects() {
    return generatedObjects;
  }

  public ProjectInstantiationSummary generatedSummary() {
    return generatedSummary;
  }

  public List<ProjectInstantiationIssue> validationIssues() {
    return validationIssues;
  }

  public Instant plannedAt() {
    return plannedAt;
  }

  public boolean creatable() {
    return validationIssues.stream()
        .noneMatch(issue -> issue.severity() == ProjectInstantiationIssue.Severity.ERROR);
  }

  public void requireCreatable() {
    if (!creatable()) {
      throw new IllegalStateException("实例化计划包含阻断问题，不能创建项目");
    }
  }

  private static void validateBlueprints(
      TemplateDefinition definition,
      List<ProjectObjectBlueprint> blueprints,
      boolean requireCompletePlan) {
    Set<UUID> objectIds = new HashSet<>();
    Set<String> componentKeys = new HashSet<>();
    Map<String, TemplateComponent> instantiable =
        definition.components().stream()
            .filter(component -> component.type().producesProjectObject())
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    TemplateComponent::componentKey, component -> component));
    for (ProjectObjectBlueprint blueprint : blueprints) {
      Objects.requireNonNull(blueprint, "实例化对象蓝图不能为空");
      TemplateComponent component = instantiable.get(blueprint.componentKey());
      if (component == null) {
        throw new IllegalArgumentException(
            "实例化对象引用了不存在或不可实例化的组件: " + blueprint.componentKey());
      }
      if (component.type() != blueprint.objectType()) {
        throw new IllegalArgumentException(
            "实例化对象类型与模板组件不一致: " + blueprint.componentKey());
      }
      if (!objectIds.add(blueprint.objectId())) {
        throw new IllegalArgumentException("实例化对象 id 重复: " + blueprint.objectId());
      }
      if (!componentKeys.add(blueprint.componentKey())) {
        throw new IllegalArgumentException("同一模板组件不能生成多个顶层实例化对象: " + blueprint.componentKey());
      }
    }
    if (requireCompletePlan && !componentKeys.equals(instantiable.keySet())) {
      Set<String> missing = new HashSet<>(instantiable.keySet());
      missing.removeAll(componentKeys);
      throw new IllegalArgumentException("可创建实例化计划缺少模板组件: " + missing);
    }
  }
}
