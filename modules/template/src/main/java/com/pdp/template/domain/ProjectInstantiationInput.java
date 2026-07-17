package com.pdp.template.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 项目创建向导提交并参与请求摘要的实例化输入。 */
public record ProjectInstantiationInput(
    String name,
    String objective,
    UUID managerId,
    LocalDate plannedStart,
    LocalDate plannedEnd,
    UUID parentProjectId,
    Map<String, Object> extensionData,
    List<OwnerAssignment> ownerAssignments,
    Map<String, Object> parameterValues) {

  public ProjectInstantiationInput {
    name = requireText(name, "项目名称", 200);
    objective = objective == null ? "" : objective.trim();
    if (objective.length() > 2000) {
      throw new IllegalArgumentException("项目目标不能超过 2000 个字符");
    }
    Objects.requireNonNull(managerId, "项目经理不能为空");
    Objects.requireNonNull(plannedStart, "计划开始日期不能为空");
    Objects.requireNonNull(plannedEnd, "计划结束日期不能为空");
    if (plannedEnd.isBefore(plannedStart)) {
      throw new IllegalArgumentException("计划结束日期不能早于计划开始日期");
    }
    extensionData = CanonicalValue.immutableMap(extensionData, "项目扩展数据");
    ownerAssignments = List.copyOf(ownerAssignments);
    parameterValues = CanonicalValue.immutableMap(parameterValues, "模板参数");
    validateOwnerAssignments(ownerAssignments);
  }

  String canonicalForm() {
    return CanonicalValue.canonical(
        Map.of(
            "name", name,
            "objective", objective,
            "managerId", managerId.toString(),
            "plannedStart", plannedStart.toString(),
            "plannedEnd", plannedEnd.toString(),
            "parentProjectId", parentProjectId == null ? "" : parentProjectId.toString(),
            "extensionData", extensionData,
            "ownerAssignments",
                ownerAssignments.stream()
                    .sorted((left, right) -> left.ruleKey().compareTo(right.ruleKey()))
                    .map(
                        assignment ->
                            Map.of(
                                "ruleKey", assignment.ruleKey(),
                                "principalId", assignment.principalId().toString()))
                    .toList(),
            "parameterValues", parameterValues));
  }

  public void requireDeclaredKeys(TemplateDefinition definition) {
    Objects.requireNonNull(definition, "模板定义不能为空");
    Set<String> declaredOwnerRules =
        definition.componentsOf(TemplateComponentType.OWNER_RULE).stream()
            .map(TemplateComponent::componentKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    for (OwnerAssignment assignment : ownerAssignments) {
      if (!declaredOwnerRules.contains(assignment.ruleKey())) {
        throw new IllegalArgumentException("负责人规则未在模板中声明: " + assignment.ruleKey());
      }
    }
    Set<String> declaredParameters = declaredParameterKeys(definition);
    for (String parameterKey : parameterValues.keySet()) {
      if (!declaredParameters.contains(parameterKey)) {
        throw new IllegalArgumentException("实例化参数未在模板中声明: " + parameterKey);
      }
    }
  }

  private static Set<String> declaredParameterKeys(TemplateDefinition definition) {
    Set<String> result = new HashSet<>();
    for (TemplateComponent component : definition.components()) {
      Object value = component.configuration().get("parameterKeys");
      if (value instanceof List<?> values) {
        for (Object item : values) {
          if (!(item instanceof String key) || key.isBlank()) {
            throw new IllegalArgumentException(
                "模板组件 parameterKeys 必须是非空字符串数组: " + component.componentKey());
          }
          result.add(key);
        }
      } else if (value != null) {
        throw new IllegalArgumentException(
            "模板组件 parameterKeys 必须是数组: " + component.componentKey());
      }
    }
    return result;
  }

  private static void validateOwnerAssignments(List<OwnerAssignment> assignments) {
    Set<String> keys = new HashSet<>();
    for (OwnerAssignment assignment : assignments) {
      Objects.requireNonNull(assignment, "负责人绑定不能为空");
      if (!keys.add(assignment.ruleKey())) {
        throw new IllegalArgumentException("负责人规则不能重复绑定: " + assignment.ruleKey());
      }
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
