package com.pdp.mysql.identity;

import com.pdp.identity.domain.ExternalIdentity;
import java.time.Instant;
import java.util.UUID;

public record ExternalIdentityRow(
    UUID id,
    UUID userId,
    String issuer,
    String subject,
    String email,
    boolean emailVerified,
    ExternalIdentity.Status status,
    Instant boundAt,
    Instant lastSynchronizedAt) {

  static ExternalIdentityRow fromDomain(ExternalIdentity identity) {
    return new ExternalIdentityRow(
        identity.id(),
        identity.userId(),
        identity.issuer(),
        identity.subject(),
        identity.email(),
        identity.emailVerified(),
        identity.status(),
        identity.boundAt(),
        identity.lastSynchronizedAt());
  }

  ExternalIdentity toDomain() {
    return new ExternalIdentity(
        id,
        userId,
        issuer,
        subject,
        email,
        emailVerified,
        status,
        boundAt,
        lastSynchronizedAt);
  }
}
