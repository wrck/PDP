package com.pdp.template.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 实例化计划中一个待原子创建的业务对象蓝图。 */
public record ProjectObjectBlueprint(
    UUID objectId,
    String componentKey,
    TemplateComponentType objectType,
    Map<String, Object> resolvedConfiguration) {

  public ProjectObjectBlueprint {
    Objects.requireNonNull(objectId, "计划对象 id 不能为空");
    if (componentKey == null || componentKey.isBlank()) {
      throw new IllegalArgumentException("计划对象 componentKey 不能为空");
    }
    Objects.requireNonNull(objectType, "计划对象类型不能为空");
    if (!objectType.producesProjectObject()) {
      throw new IllegalArgumentException(objectType + " 不是可实例化项目对象");
    }
    resolvedConfiguration =
        CanonicalValue.immutableMap(resolvedConfiguration, "计划对象解析配置");
  }
}
