package com.pdp.integration.event;

import com.pdp.integration.event.EventDeliveryStore.ClaimResult;
import com.pdp.integration.event.EventDeliveryStore.Decision;
import com.pdp.integration.event.EventDeliveryStore.Delivery;
import java.time.Clock;
import java.util.Objects;

/**
 * Applies at-least-once delivery semantics without allowing consumer failure to escape into the
 * already committed core business transaction.
 */
public final class IdempotentEventDispatcher {

  private final EventDeliveryStore deliveryStore;
  private final Clock clock;
  private final int maximumAttempts;

  public IdempotentEventDispatcher(
      EventDeliveryStore deliveryStore, Clock clock, int maximumAttempts) {
    this.deliveryStore = Objects.requireNonNull(deliveryStore, "deliveryStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (maximumAttempts < 1) {
      throw new IllegalArgumentException("maximumAttempts must be at least 1");
    }
    this.maximumAttempts = maximumAttempts;
  }

  public EventDispatchOutcome dispatch(
      String listenerId, DomainEventEnvelope event, DomainEventHandler handler) {
    Objects.requireNonNull(handler, "handler");
    ClaimResult claim = deliveryStore.claim(listenerId, event, clock.instant());
    if (claim.decision() != Decision.ACCEPTED) {
      return new EventDispatchOutcome(claim.decision(), claim.delivery());
    }

    try {
      handler.handle(event);
      Delivery completed =
          deliveryStore.complete(listenerId, event.eventId(), clock.instant());
      return new EventDispatchOutcome(Decision.ACCEPTED, java.util.Optional.of(completed));
    } catch (Exception failure) {
      Delivery failed =
          deliveryStore.fail(
              listenerId, event.eventId(), failure, maximumAttempts, clock.instant());
      return new EventDispatchOutcome(Decision.ACCEPTED, java.util.Optional.of(failed));
    }
  }

  public EventDispatchOutcome replayDeadLetter(
      String listenerId, DomainEventEnvelope event, DomainEventHandler handler) {
    if (!deliveryStore.requeueDeadLetter(listenerId, event.eventId(), clock.instant())) {
      return deliveryStore
          .find(listenerId, event.eventId())
          .map(
              delivery ->
                  new EventDispatchOutcome(Decision.IN_PROGRESS, java.util.Optional.of(delivery)))
          .orElseGet(
              () ->
                  new EventDispatchOutcome(Decision.DEAD_LETTER, java.util.Optional.empty()));
    }
    return dispatch(listenerId, event, handler);
  }
}
