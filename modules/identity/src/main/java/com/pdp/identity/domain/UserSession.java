package com.pdp.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserSession(
    UUID id,
    UUID userId,
    String identityProviderSessionId,
    String refreshCredentialReference,
    Instant issuedAt,
    Instant lastSeenAt,
    Instant expiresAt,
    long authorizationVersion,
    Status status,
    Instant revokedAt,
    String revocationReason) {

  public enum Status {
    ACTIVE,
    EXPIRED,
    REVOKED
  }

  public UserSession {
    Objects.requireNonNull(id);
    Objects.requireNonNull(userId);
    Objects.requireNonNull(issuedAt);
    Objects.requireNonNull(lastSeenAt);
    Objects.requireNonNull(expiresAt);
    Objects.requireNonNull(status);
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("会话过期时间必须晚于签发时间");
    }
  }

  public static UserSession active(
      UUID id,
      UUID userId,
      String identityProviderSessionId,
      String refreshCredentialReference,
      Instant issuedAt,
      Instant expiresAt,
      long authorizationVersion) {
    return new UserSession(
        id,
        userId,
        identityProviderSessionId,
        refreshCredentialReference,
        issuedAt,
        issuedAt,
        expiresAt,
        authorizationVersion,
        Status.ACTIVE,
        null,
        null);
  }

  public UserSession revoke(Instant at, String reason) {
    if (status != Status.ACTIVE) {
      return this;
    }
    return new UserSession(
        id,
        userId,
        identityProviderSessionId,
        refreshCredentialReference,
        issuedAt,
        lastSeenAt,
        expiresAt,
        authorizationVersion,
        Status.REVOKED,
        at,
        reason);
  }
}
