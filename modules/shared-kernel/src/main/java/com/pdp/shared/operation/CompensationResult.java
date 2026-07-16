package com.pdp.shared.operation;

import java.time.Instant;
import java.util.UUID;

public record CompensationResult(
    UUID operationId, Status status, String evidenceReference, Instant completedAt) {
  public enum Status {
    ACCEPTED,
    COMPLETED,
    FAILED
  }
}
