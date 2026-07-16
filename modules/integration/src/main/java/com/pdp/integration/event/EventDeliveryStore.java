package com.pdp.integration.event;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for per-listener idempotency, ordering checkpoints, retries, and dead letters.
 */
public interface EventDeliveryStore {

  ClaimResult claim(String listenerId, DomainEventEnvelope event, Instant claimedAt);

  Delivery complete(String listenerId, UUID eventId, Instant completedAt);

  Delivery fail(
      String listenerId,
      UUID eventId,
      Throwable failure,
      int maximumAttempts,
      Instant failedAt);

  boolean requeueDeadLetter(String listenerId, UUID eventId, Instant requeuedAt);

  Optional<Delivery> find(String listenerId, UUID eventId);

  enum Decision {
    ACCEPTED,
    DUPLICATE,
    STALE,
    OUT_OF_ORDER,
    IN_PROGRESS,
    DEAD_LETTER
  }

  enum Status {
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
  }

  record ClaimResult(Decision decision, Optional<Delivery> delivery) {

    public ClaimResult {
      delivery = delivery == null ? Optional.empty() : delivery;
    }
  }

  record Delivery(
      String listenerId,
      DomainEventEnvelope event,
      Status status,
      int attempts,
      Instant updatedAt,
      String errorSummary) {}
}
