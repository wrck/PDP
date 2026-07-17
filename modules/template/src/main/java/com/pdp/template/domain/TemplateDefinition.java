package com.pdp.template.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 一个模板版本的完整、不可变且引用闭合的定义。 */
public final class TemplateDefinition {
  private static final Comparator<TemplateComponent> CANONICAL_ORDER =
      Comparator.comparingInt(TemplateComponent::sequence)
          .thenComparing(TemplateComponent::componentKey);

  private final List<TemplateComponent> components;
  private final Map<String, TemplateComponent> componentsByKey;

  public TemplateDefinition(List<TemplateComponent> components) {
    Objects.requireNonNull(components, "模板定义不能为空");
    this.components = List.copyOf(components);
    this.componentsByKey = indexAndValidate(this.components);
  }

  public List<TemplateComponent> components() {
    return components;
  }

  public List<TemplateComponent> componentsOf(TemplateComponentType type) {
    Objects.requireNonNull(type, "组件类型不能为空");
    return components.stream().filter(component -> component.type() == type).sorted(CANONICAL_ORDER).toList();
  }

  public TemplateComponent requireComponent(String componentKey) {
    TemplateComponent component = componentsByKey.get(componentKey);
    if (component == null) {
      throw new IllegalArgumentException("模板组件不存在: " + componentKey);
    }
    return component;
  }

  String canonicalForm() {
    Map<TemplateComponentType, List<TemplateComponent>> grouped =
        new EnumMap<>(TemplateComponentType.class);
    for (TemplateComponentType type : TemplateComponentType.values()) {
      grouped.put(type, new ArrayList<>());
    }
    components.forEach(component -> grouped.get(component.type()).add(component));

    StringBuilder result = new StringBuilder("project-template-definition-v1{");
    for (TemplateComponentType type : TemplateComponentType.values()) {
      result.append(type.name()).append('[');
      grouped.get(type).stream()
          .sorted(CANONICAL_ORDER)
          .forEach(
              component ->
                  result
                      .append(CanonicalValue.canonical(component.componentKey()))
                      .append(CanonicalValue.canonical(component.name()))
                      .append("#")
                      .append(component.sequence())
                      .append(CanonicalValue.canonical(component.parentComponentKey()))
                      .append(CanonicalValue.canonical(component.configuration()))
                      .append(';'));
      result.append(']');
    }
    return result.append('}').toString();
  }

  private static Map<String, TemplateComponent> indexAndValidate(
      List<TemplateComponent> components) {
    Map<String, TemplateComponent> indexed = new HashMap<>();
    for (TemplateComponent component : components) {
      Objects.requireNonNull(component, "模板组件不能为空");
      if (indexed.putIfAbsent(component.componentKey(), component) != null) {
        throw new IllegalArgumentException("模板组件稳定键重复: " + component.componentKey());
      }
    }
    for (TemplateComponent component : components) {
      validateParent(component, indexed);
    }
    validateAcyclic(indexed);
    return Map.copyOf(indexed);
  }

  private static void validateParent(
      TemplateComponent component, Map<String, TemplateComponent> components) {
    if (component.parentComponentKey() == null) {
      return;
    }
    TemplateComponent parent = components.get(component.parentComponentKey());
    if (parent == null) {
      throw new IllegalArgumentException(
          "模板组件父引用不存在: " + component.componentKey() + " -> " + component.parentComponentKey());
    }
    if (!component.type().acceptsParent(parent.type())) {
      throw new IllegalArgumentException(
          "模板组件父引用类型非法: "
              + component.type()
              + " 不能引用 "
              + parent.type()
              + " 作为父组件");
    }
  }

  private static void validateAcyclic(Map<String, TemplateComponent> components) {
    Set<String> completed = new HashSet<>();
    for (String start : components.keySet()) {
      if (completed.contains(start)) {
        continue;
      }
      Set<String> visiting = new HashSet<>();
      ArrayDeque<String> path = new ArrayDeque<>();
      String current = start;
      while (current != null && !completed.contains(current)) {
        if (!visiting.add(current)) {
          path.addLast(current);
          throw new IllegalArgumentException("模板组件父引用存在环: " + String.join(" -> ", path));
        }
        path.addLast(current);
        current = components.get(current).parentComponentKey();
      }
      completed.addAll(visiting);
    }
  }
}
