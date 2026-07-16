package com.pdp.integration.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.integration.event.EventDeliveryStore.Decision;
import com.pdp.integration.event.EventDeliveryStore.Status;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EventDeliverySemanticsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void publishesOnlyInsideAuthoritativeTransaction() {
    List<Object> published = new ArrayList<>();
    TransactionalOutboxPublisher publisher = new TransactionalOutboxPublisher(published::add);
    DomainEventEnvelope event = event(UUID.randomUUID(), 1);

    assertThatThrownBy(() -> publisher.publish(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authoritative business transaction");

    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:event-outbox;DB_CLOSE_DELAY=-1");
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    transaction.executeWithoutResult(ignored -> publisher.publish(event));

    assertThat(published).containsExactly(event);
  }

  @Test
  void liquibaseOwnsTheExactSpringModulithJdbcOutboxSchema() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:event-schema;DB_CLOSE_DELAY=-1");

    try (var connection = dataSource.getConnection()) {
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (Liquibase liquibase =
          new Liquibase(
              "db/changelog/db.changelog-master.xml",
              new ClassLoaderResourceAccessor(getClass().getClassLoader()),
              database)) {
        liquibase.update(new Contexts());
      }
    }

    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    List<String> columns =
        jdbc.queryForList(
            """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'EVENT_PUBLICATION'
            ORDER BY ORDINAL_POSITION
            """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "ID",
            "LISTENER_ID",
            "EVENT_TYPE",
            "SERIALIZED_EVENT",
            "PUBLICATION_DATE",
            "COMPLETION_DATE",
            "STATUS",
            "COMPLETION_ATTEMPTS",
            "LAST_RESUBMISSION_DATE");
  }

  @Test
  void duplicateDeliveryIsHandledOnceAndListenerStateIsIndependent() {
    ConcurrentEventDeliveryStore store = new ConcurrentEventDeliveryStore();
    IdempotentEventDispatcher dispatcher = new IdempotentEventDispatcher(store, CLOCK, 3);
    AtomicInteger projectionCalls = new AtomicInteger();
    AtomicInteger notificationCalls = new AtomicInteger();
    DomainEventEnvelope event = event(UUID.randomUUID(), 1);

    EventDispatchOutcome first =
        dispatcher.dispatch("project-projection", event, ignored -> projectionCalls.incrementAndGet());
    EventDispatchOutcome duplicate =
        dispatcher.dispatch("project-projection", event, ignored -> projectionCalls.incrementAndGet());
    EventDispatchOutcome independentListener =
        dispatcher.dispatch("notification", event, ignored -> notificationCalls.incrementAndGet());

    assertThat(first.delivery()).hasValueSatisfying(it -> assertThat(it.status()).isEqualTo(Status.COMPLETED));
    assertThat(duplicate.decision()).isEqualTo(Decision.DUPLICATE);
    assertThat(independentListener.delivery())
        .hasValueSatisfying(it -> assertThat(it.status()).isEqualTo(Status.COMPLETED));
    assertThat(projectionCalls).hasValue(1);
    assertThat(notificationCalls).hasValue(1);
  }

  @Test
  void staleAndOutOfOrderEventsCannotOverwriteNewerProjection() {
    ConcurrentEventDeliveryStore store = new ConcurrentEventDeliveryStore();
    IdempotentEventDispatcher dispatcher = new IdempotentEventDispatcher(store, CLOCK, 3);
    UUID aggregateId = UUID.randomUUID();
    AtomicInteger projectedRevision = new AtomicInteger();

    dispatcher.dispatch(
        "project-projection",
        event(aggregateId, 1),
        envelope -> projectedRevision.set(Math.toIntExact(envelope.aggregateRevision())));
    EventDispatchOutcome gap =
        dispatcher.dispatch(
            "project-projection",
            event(aggregateId, 3),
            envelope -> projectedRevision.set(Math.toIntExact(envelope.aggregateRevision())));
    dispatcher.dispatch(
        "project-projection",
        event(aggregateId, 2),
        envelope -> projectedRevision.set(Math.toIntExact(envelope.aggregateRevision())));
    EventDispatchOutcome revisionThree =
        dispatcher.dispatch(
            "project-projection",
            event(aggregateId, 3),
            envelope -> projectedRevision.set(Math.toIntExact(envelope.aggregateRevision())));
    EventDispatchOutcome stale =
        dispatcher.dispatch(
            "project-projection",
            event(aggregateId, 1),
            envelope -> projectedRevision.set(Math.toIntExact(envelope.aggregateRevision())));

    assertThat(gap.decision()).isEqualTo(Decision.OUT_OF_ORDER);
    assertThat(revisionThree.delivery())
        .hasValueSatisfying(it -> assertThat(it.status()).isEqualTo(Status.COMPLETED));
    assertThat(stale.decision()).isEqualTo(Decision.STALE);
    assertThat(projectedRevision).hasValue(3);
  }

  @Test
  void repeatedFailureMovesToDeadLetterAndReplayDoesNotRollbackCoreFact() {
    ConcurrentEventDeliveryStore store = new ConcurrentEventDeliveryStore();
    IdempotentEventDispatcher dispatcher = new IdempotentEventDispatcher(store, CLOCK, 2);
    DomainEventEnvelope event = event(UUID.randomUUID(), 1);
    AtomicBoolean coreFactCommitted = new AtomicBoolean(true);
    AtomicBoolean downstreamAvailable = new AtomicBoolean(false);
    AtomicInteger calls = new AtomicInteger();

    DomainEventHandler handler =
        ignored -> {
          calls.incrementAndGet();
          if (!downstreamAvailable.get()) {
            throw new IllegalStateException("downstream unavailable");
          }
        };

    EventDispatchOutcome first = dispatcher.dispatch("search", event, handler);
    EventDispatchOutcome second = dispatcher.dispatch("search", event, handler);
    EventDispatchOutcome blocked = dispatcher.dispatch("search", event, handler);

    assertThat(first.delivery())
        .hasValueSatisfying(
            it -> {
              assertThat(it.status()).isEqualTo(Status.FAILED);
              assertThat(it.attempts()).isEqualTo(1);
            });
    assertThat(second.delivery())
        .hasValueSatisfying(
            it -> {
              assertThat(it.status()).isEqualTo(Status.DEAD_LETTER);
              assertThat(it.attempts()).isEqualTo(2);
              assertThat(it.errorSummary()).contains("downstream unavailable");
            });
    assertThat(blocked.decision()).isEqualTo(Decision.DEAD_LETTER);
    assertThat(coreFactCommitted).isTrue();

    downstreamAvailable.set(true);
    EventDispatchOutcome replayed = dispatcher.replayDeadLetter("search", event, handler);
    EventDispatchOutcome duplicate = dispatcher.dispatch("search", event, handler);

    assertThat(replayed.delivery())
        .hasValueSatisfying(it -> assertThat(it.status()).isEqualTo(Status.COMPLETED));
    assertThat(duplicate.decision()).isEqualTo(Decision.DUPLICATE);
    assertThat(calls).hasValue(3);
  }

  private static DomainEventEnvelope event(UUID aggregateId, long aggregateRevision) {
    return new DomainEventEnvelope(
        UUID.randomUUID(),
        "pdp.project.lifecycle.changed",
        1,
        CLOCK.instant(),
        UUID.fromString("01900000-0000-7000-8000-000000000001"),
        "project",
        aggregateId,
        aggregateRevision,
        new DomainEventEnvelope.Actor("USER", "01900000-0000-7000-8000-000000000002"),
        "4bf92f3577b34da6a3ce929d0e0e4736",
        Map.of("state", "ACTIVE"),
        Map.of("source", "pdp", "classification", "INTERNAL"));
  }
}
