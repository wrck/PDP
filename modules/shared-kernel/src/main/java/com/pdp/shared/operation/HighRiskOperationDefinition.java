package com.pdp.shared.operation;

public record HighRiskOperationDefinition(
    String operationType,
    String irreversiblePoint,
    boolean compensationRequired,
    String compensationSummary) {
  public HighRiskOperationDefinition {
    if (operationType == null || operationType.isBlank()) {
      throw new IllegalArgumentException("操作类型不能为空");
    }
    if (irreversiblePoint == null || irreversiblePoint.isBlank()) {
      throw new IllegalArgumentException("不可逆点不能为空");
    }
    if (compensationSummary == null || compensationSummary.isBlank()) {
      throw new IllegalArgumentException("补偿说明不能为空");
    }
  }
}
