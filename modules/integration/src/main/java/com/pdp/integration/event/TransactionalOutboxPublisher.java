package com.pdp.integration.event;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes an event only while the authoritative business transaction is active.
 *
 * <p>Spring Modulith JDBC listeners persist the publication in that transaction and deliver it
 * after commit.
 */
public final class TransactionalOutboxPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public TransactionalOutboxPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
  }

  public void publish(DomainEventEnvelope event) {
    Objects.requireNonNull(event, "event");
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "domain events must be published inside the authoritative business transaction");
    }
    eventPublisher.publishEvent(event);
  }
}
