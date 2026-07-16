package com.pdp.shared.operation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OperationPreviewRequest(
    String operationType,
    String targetType,
    List<UUID> targetIds,
    Map<UUID, Long> expectedRevisions,
    String commandDigest) {
  public OperationPreviewRequest {
    targetIds = List.copyOf(targetIds);
    expectedRevisions = Map.copyOf(expectedRevisions);
    if (operationType == null || operationType.isBlank() || targetType == null || targetType.isBlank()) {
      throw new IllegalArgumentException("操作类型和目标类型不能为空");
    }
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException("至少需要一个操作目标");
    }
    if (commandDigest == null || commandDigest.isBlank()) {
      throw new IllegalArgumentException("命令摘要不能为空");
    }
  }
}
