package com.pdp.shared.operation;

import java.time.Instant;
import java.util.UUID;

public record OperationConfirmation(
    UUID previewId,
    String operationType,
    String commandDigest,
    String revisionDigest,
    Instant previewedAt) {
  public OperationConfirmation {
    if (previewId == null
        || previewedAt == null
        || operationType == null
        || operationType.isBlank()
        || commandDigest == null
        || commandDigest.isBlank()
        || revisionDigest == null
        || revisionDigest.isBlank()) {
      throw new IllegalArgumentException("操作确认字段不能为空");
    }
  }
}
