package com.pdp.identity.application;

import java.time.Instant;
import java.util.UUID;

public record AuthorizationRevoked(
    UUID userId,
    long authorizationVersion,
    String reason,
    Instant occurredAt,
    Instant localCacheDeadline,
    Instant searchProjectionDeadline,
    Instant realtimeSessionDeadline,
    Instant sessionRevocationDeadline) {

  public static AuthorizationRevoked create(
      UUID userId,
      long authorizationVersion,
      String reason,
      Instant occurredAt,
      PermissionRevocationSla sla) {
    return new AuthorizationRevoked(
        userId,
        authorizationVersion,
        reason,
        occurredAt,
        occurredAt.plus(sla.localCache()),
        occurredAt.plus(sla.searchProjection()),
        occurredAt.plus(sla.realtimeSession()),
        occurredAt.plus(sla.activeSession()));
  }
}
