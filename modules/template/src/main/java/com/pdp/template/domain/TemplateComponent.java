package com.pdp.template.domain;

import com.pdp.shared.concurrency.Revision;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 项目模板版本内具有稳定键的一个默认计划组件。 */
public record TemplateComponent(
    UUID id,
    TemplateComponentType type,
    String componentKey,
    String name,
    int sequence,
    String parentComponentKey,
    Map<String, Object> configuration,
    Revision revision) {
  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{1,99}");

  public TemplateComponent {
    Objects.requireNonNull(id, "模板组件 id 不能为空");
    Objects.requireNonNull(type, "模板组件类型不能为空");
    Objects.requireNonNull(revision, "模板组件 revision 不能为空");
    componentKey = requireText(componentKey, "模板组件稳定键", 100);
    if (!KEY_PATTERN.matcher(componentKey).matches()) {
      throw new IllegalArgumentException("模板组件稳定键格式非法: " + componentKey);
    }
    name = requireText(name, "模板组件名称", 200);
    if (sequence < 0) {
      throw new IllegalArgumentException("模板组件顺序不能小于 0");
    }
    if (parentComponentKey != null) {
      parentComponentKey = requireText(parentComponentKey, "父组件稳定键", 100);
      if (!KEY_PATTERN.matcher(parentComponentKey).matches()) {
        throw new IllegalArgumentException("父组件稳定键格式非法: " + parentComponentKey);
      }
    }
    configuration = CanonicalValue.immutableMap(configuration, "模板组件配置");
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
