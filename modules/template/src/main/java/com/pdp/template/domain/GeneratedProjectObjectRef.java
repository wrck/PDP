package com.pdp.template.domain;

import java.util.Objects;
import java.util.UUID;

/** 完成实例化后模板组件与已生成业务对象之间的稳定映射。 */
public record GeneratedProjectObjectRef(
    String componentKey, TemplateComponentType objectType, UUID objectId) {
  public GeneratedProjectObjectRef {
    if (componentKey == null || componentKey.isBlank()) {
      throw new IllegalArgumentException("生成对象 componentKey 不能为空");
    }
    Objects.requireNonNull(objectType, "生成对象类型不能为空");
    if (!objectType.producesProjectObject()) {
      throw new IllegalArgumentException(objectType + " 不是可生成的项目对象");
    }
    Objects.requireNonNull(objectId, "生成对象 id 不能为空");
  }
}
