package com.pdp.shared.operation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OperationImpactPreview(
    UUID previewId,
    String operationType,
    Map<String, Long> affectedCounts,
    List<String> warnings,
    String irreversibleAt,
    String compensation,
    String confirmationToken,
    Instant expiresAt) {
  public OperationImpactPreview {
    affectedCounts = Map.copyOf(affectedCounts);
    warnings = List.copyOf(warnings);
  }
}
