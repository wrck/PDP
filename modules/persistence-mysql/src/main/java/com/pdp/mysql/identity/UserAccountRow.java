package com.pdp.mysql.identity;

import com.pdp.identity.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserAccountRow(
    UUID id,
    String externalSubject,
    String displayName,
    String email,
    UserAccount.Status status,
    String locale,
    String timezone,
    Instant lastLoginAt,
    long authorizationVersion) {

  static UserAccountRow fromDomain(UserAccount account) {
    return new UserAccountRow(
        account.id(),
        account.externalSubject(),
        account.displayName(),
        account.email(),
        account.status(),
        account.locale(),
        account.timezone(),
        account.lastLoginAt(),
        account.authorizationVersion());
  }

  UserAccount toDomain() {
    return new UserAccount(
        id,
        externalSubject,
        displayName,
        email,
        status,
        locale,
        timezone,
        lastLoginAt,
        authorizationVersion);
  }
}
