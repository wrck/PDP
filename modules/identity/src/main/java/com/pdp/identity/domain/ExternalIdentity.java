package com.pdp.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExternalIdentity(
    UUID id,
    UUID userId,
    String issuer,
    String subject,
    String email,
    boolean emailVerified,
    Status status,
    Instant boundAt,
    Instant lastSynchronizedAt) {

  public enum Status {
    ACTIVE,
    UNBOUND,
    REVOKED
  }

  public ExternalIdentity {
    Objects.requireNonNull(id);
    Objects.requireNonNull(userId);
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("OIDC issuer 和 subject 不能为空");
    }
    Objects.requireNonNull(status);
    Objects.requireNonNull(boundAt);
    Objects.requireNonNull(lastSynchronizedAt);
  }

  public static ExternalIdentity bind(
      UUID userId,
      String issuer,
      String subject,
      String email,
      boolean emailVerified,
      Instant at) {
    return new ExternalIdentity(
        UUID.randomUUID(),
        userId,
        issuer,
        subject,
        email,
        emailVerified,
        Status.ACTIVE,
        at,
        at);
  }

  public ExternalIdentity synchronize(String email, boolean emailVerified, Instant at) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("非活动外部身份不能同步");
    }
    return new ExternalIdentity(
        id, userId, issuer, subject, email, emailVerified, status, boundAt, at);
  }
}
