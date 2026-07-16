package com.pdp.shared.operation;

import java.util.LinkedHashMap;
import java.util.Map;

/** 高风险操作定义注册表。 */
public final class HighRiskOperationCatalog {
  private final Map<String, HighRiskOperationDefinition> definitions;

  public HighRiskOperationCatalog(Map<String, HighRiskOperationDefinition> definitions) {
    this.definitions = Map.copyOf(definitions);
  }

  public HighRiskOperationDefinition require(String operationType) {
    var definition = definitions.get(operationType);
    if (definition == null) {
      throw new IllegalArgumentException("未注册的高风险操作: " + operationType);
    }
    return definition;
  }

  public static HighRiskOperationCatalog p1Defaults() {
    var definitions = new LinkedHashMap<String, HighRiskOperationDefinition>();
    register(definitions, HighRiskOperationType.DOMAIN_PACKAGE_RELEASE, "发布版本生效");
    register(definitions, HighRiskOperationType.PROJECT_ROLLBACK, "回退命令提交");
    register(definitions, HighRiskOperationType.BASELINE_REPLACEMENT, "新基线生效");
    register(definitions, HighRiskOperationType.MANUAL_PROGRESS_ADJUSTMENT, "人工进度写入");
    register(definitions, HighRiskOperationType.DELIVERABLE_RELEASE, "交付件版本发布");
    register(definitions, HighRiskOperationType.APPROVAL_FINALIZATION, "审批终态写入");
    register(definitions, HighRiskOperationType.DATA_DISPOSITION, "数据物理处置");
    register(definitions, HighRiskOperationType.LEGACY_CUTOVER, "目标写权限开放");
    register(definitions, HighRiskOperationType.DATABASE_SWITCH, "目标部署取得写主权");
    return new HighRiskOperationCatalog(definitions);
  }

  private static void register(
      Map<String, HighRiskOperationDefinition> definitions, String type, String point) {
    definitions.put(
        type,
        new HighRiskOperationDefinition(type, point, true, "失败后执行受控回退或人工补偿并保留审计证据"));
  }
}
