package com.pdp.mysql.identity;

import com.pdp.identity.domain.UserSession;
import java.time.Instant;
import java.util.UUID;

public record UserSessionRow(
    UUID id,
    UUID userId,
    String identityProviderSessionId,
    String refreshCredentialReference,
    Instant issuedAt,
    Instant lastSeenAt,
    Instant expiresAt,
    long authorizationVersion,
    UserSession.Status status,
    Instant revokedAt,
    String revocationReason) {

  static UserSessionRow fromDomain(UserSession session) {
    return new UserSessionRow(
        session.id(),
        session.userId(),
        session.identityProviderSessionId(),
        session.refreshCredentialReference(),
        session.issuedAt(),
        session.lastSeenAt(),
        session.expiresAt(),
        session.authorizationVersion(),
        session.status(),
        session.revokedAt(),
        session.revocationReason());
  }

  UserSession toDomain() {
    return new UserSession(
        id,
        userId,
        identityProviderSessionId,
        refreshCredentialReference,
        issuedAt,
        lastSeenAt,
        expiresAt,
        authorizationVersion,
        status,
        revokedAt,
        revocationReason);
  }
}
