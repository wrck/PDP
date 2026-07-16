package com.pdp.integration.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable, persistence-neutral envelope shared by internal platform event publishers and consumers.
 */
public record DomainEventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    UUID workspaceId,
    String aggregateType,
    UUID aggregateId,
    long aggregateRevision,
    Actor actor,
    String traceId,
    Map<String, Object> data,
    Map<String, String> metadata) {

  public DomainEventEnvelope {
    Objects.requireNonNull(eventId, "eventId");
    eventType = requireText(eventType, "eventType");
    if (eventVersion < 1) {
      throw new IllegalArgumentException("eventVersion must be at least 1");
    }
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(workspaceId, "workspaceId");
    aggregateType = requireText(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    if (aggregateRevision < 0) {
      throw new IllegalArgumentException("aggregateRevision must not be negative");
    }
    Objects.requireNonNull(actor, "actor");
    traceId = requireText(traceId, "traceId");
    data = Map.copyOf(Objects.requireNonNull(data, "data"));
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  public record Actor(String type, String id) {

    public Actor {
      type = requireText(type, "actor.type");
      id = requireText(id, "actor.id");
    }
  }
}
