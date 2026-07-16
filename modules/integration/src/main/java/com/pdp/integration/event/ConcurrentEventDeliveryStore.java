package com.pdp.integration.event;

import com.pdp.integration.event.EventDeliveryStore.ClaimResult;
import com.pdp.integration.event.EventDeliveryStore.Decision;
import com.pdp.integration.event.EventDeliveryStore.Delivery;
import com.pdp.integration.event.EventDeliveryStore.Status;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-safe reference implementation for local execution and contract tests.
 *
 * <p>Production deployments should provide a durable implementation of {@link EventDeliveryStore}.
 */
public final class ConcurrentEventDeliveryStore implements EventDeliveryStore {

  private final Map<DeliveryKey, Delivery> deliveries = new HashMap<>();
  private final Map<AggregateKey, Long> completedRevisions = new HashMap<>();

  @Override
  public synchronized ClaimResult claim(
      String listenerId, DomainEventEnvelope event, Instant claimedAt) {
    requireListener(listenerId);
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(claimedAt, "claimedAt");

    DeliveryKey deliveryKey = new DeliveryKey(listenerId, event.eventId());
    Delivery existing = deliveries.get(deliveryKey);
    if (existing != null) {
      return claimExisting(deliveryKey, existing, claimedAt);
    }

    AggregateKey aggregateKey =
        new AggregateKey(listenerId, event.aggregateType(), event.aggregateId());
    Long completedRevision = completedRevisions.get(aggregateKey);
    if (completedRevision != null && event.aggregateRevision() <= completedRevision) {
      return new ClaimResult(Decision.STALE, Optional.empty());
    }
    if (completedRevision != null && event.aggregateRevision() > completedRevision + 1) {
      return new ClaimResult(Decision.OUT_OF_ORDER, Optional.empty());
    }

    Delivery accepted = new Delivery(listenerId, event, Status.PROCESSING, 0, claimedAt, null);
    deliveries.put(deliveryKey, accepted);
    return new ClaimResult(Decision.ACCEPTED, Optional.of(accepted));
  }

  private ClaimResult claimExisting(
      DeliveryKey deliveryKey, Delivery existing, Instant claimedAt) {
    return switch (existing.status()) {
      case COMPLETED -> new ClaimResult(Decision.DUPLICATE, Optional.of(existing));
      case PROCESSING -> new ClaimResult(Decision.IN_PROGRESS, Optional.of(existing));
      case DEAD_LETTER -> new ClaimResult(Decision.DEAD_LETTER, Optional.of(existing));
      case FAILED -> {
        Delivery retrying =
            new Delivery(
                existing.listenerId(),
                existing.event(),
                Status.PROCESSING,
                existing.attempts(),
                claimedAt,
                existing.errorSummary());
        deliveries.put(deliveryKey, retrying);
        yield new ClaimResult(Decision.ACCEPTED, Optional.of(retrying));
      }
    };
  }

  @Override
  public synchronized Delivery complete(String listenerId, UUID eventId, Instant completedAt) {
    DeliveryKey key = new DeliveryKey(requireListener(listenerId), Objects.requireNonNull(eventId));
    Delivery current = requireProcessing(key);
    Delivery completed =
        new Delivery(
            listenerId,
            current.event(),
            Status.COMPLETED,
            current.attempts(),
            Objects.requireNonNull(completedAt),
            null);
    deliveries.put(key, completed);
    AggregateKey aggregateKey =
        new AggregateKey(
            listenerId, current.event().aggregateType(), current.event().aggregateId());
    completedRevisions.merge(
        aggregateKey, current.event().aggregateRevision(), Math::max);
    return completed;
  }

  @Override
  public synchronized Delivery fail(
      String listenerId,
      UUID eventId,
      Throwable failure,
      int maximumAttempts,
      Instant failedAt) {
    if (maximumAttempts < 1) {
      throw new IllegalArgumentException("maximumAttempts must be at least 1");
    }
    DeliveryKey key = new DeliveryKey(requireListener(listenerId), Objects.requireNonNull(eventId));
    Delivery current = requireProcessing(key);
    int attempts = current.attempts() + 1;
    Status status = attempts >= maximumAttempts ? Status.DEAD_LETTER : Status.FAILED;
    Delivery failed =
        new Delivery(
            listenerId,
            current.event(),
            status,
            attempts,
            Objects.requireNonNull(failedAt),
            summarize(Objects.requireNonNull(failure)));
    deliveries.put(key, failed);
    return failed;
  }

  @Override
  public synchronized boolean requeueDeadLetter(
      String listenerId, UUID eventId, Instant requeuedAt) {
    DeliveryKey key = new DeliveryKey(requireListener(listenerId), Objects.requireNonNull(eventId));
    Delivery current = deliveries.get(key);
    if (current == null || current.status() != Status.DEAD_LETTER) {
      return false;
    }
    deliveries.put(
        key,
        new Delivery(
            listenerId,
            current.event(),
            Status.FAILED,
            0,
            Objects.requireNonNull(requeuedAt),
            current.errorSummary()));
    return true;
  }

  @Override
  public synchronized Optional<Delivery> find(String listenerId, UUID eventId) {
    return Optional.ofNullable(
        deliveries.get(
            new DeliveryKey(requireListener(listenerId), Objects.requireNonNull(eventId))));
  }

  private Delivery requireProcessing(DeliveryKey key) {
    Delivery delivery = deliveries.get(key);
    if (delivery == null || delivery.status() != Status.PROCESSING) {
      throw new IllegalStateException("delivery must be claimed before it is updated");
    }
    return delivery;
  }

  private static String requireListener(String listenerId) {
    if (listenerId == null || listenerId.isBlank()) {
      throw new IllegalArgumentException("listenerId must not be blank");
    }
    return listenerId;
  }

  private static String summarize(Throwable failure) {
    String message = failure.getMessage();
    String summary = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    return summary.length() <= 512 ? summary : summary.substring(0, 512);
  }

  private record DeliveryKey(String listenerId, UUID eventId) {}

  private record AggregateKey(String listenerId, String aggregateType, UUID aggregateId) {}
}
