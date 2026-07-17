package com.pdp.template.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 实例化计划或完成记录中的各类生成对象数量。 */
public record ProjectInstantiationSummary(Map<TemplateComponentType, Integer> counts) {
  public ProjectInstantiationSummary {
    Objects.requireNonNull(counts, "实例化汇总不能为空");
    EnumMap<TemplateComponentType, Integer> normalized =
        new EnumMap<>(TemplateComponentType.class);
    for (TemplateComponentType type : TemplateComponentType.values()) {
      if (type.producesProjectObject()) {
        int count = counts.getOrDefault(type, 0);
        if (count < 0) {
          throw new IllegalArgumentException("实例化汇总数量不能小于 0: " + type);
        }
        normalized.put(type, count);
      }
    }
    counts = Map.copyOf(normalized);
  }

  public static ProjectInstantiationSummary fromBlueprints(
      List<ProjectObjectBlueprint> blueprints) {
    Objects.requireNonNull(blueprints, "实例化对象蓝图不能为空");
    EnumMap<TemplateComponentType, Integer> counts =
        new EnumMap<>(TemplateComponentType.class);
    blueprints.forEach(
        blueprint -> {
          Objects.requireNonNull(blueprint, "实例化对象蓝图不能为空");
          counts.merge(blueprint.objectType(), 1, Integer::sum);
        });
    return new ProjectInstantiationSummary(counts);
  }

  public static ProjectInstantiationSummary fromGeneratedObjects(
      List<GeneratedProjectObjectRef> generatedObjects) {
    Objects.requireNonNull(generatedObjects, "生成对象引用不能为空");
    EnumMap<TemplateComponentType, Integer> counts =
        new EnumMap<>(TemplateComponentType.class);
    generatedObjects.forEach(
        generated -> {
          Objects.requireNonNull(generated, "生成对象引用不能为空");
          counts.merge(generated.objectType(), 1, Integer::sum);
        });
    return new ProjectInstantiationSummary(counts);
  }

  public int count(TemplateComponentType type) {
    if (!type.producesProjectObject()) {
      return 0;
    }
    return counts.getOrDefault(type, 0);
  }
}
