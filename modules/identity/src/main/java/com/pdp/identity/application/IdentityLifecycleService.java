package com.pdp.identity.application;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.port.AuthorizationRevocationPublisher;
import com.pdp.identity.port.RefreshCredentialRevoker;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.identity.port.UserSessionRepository;
import java.time.Clock;
import java.util.UUID;

/** 统一处理用户生命周期、活动会话和刷新凭据撤销。 */
public final class IdentityLifecycleService {
  private final UserAccountRepository accounts;
  private final UserSessionRepository sessions;
  private final RefreshCredentialRevoker refreshCredentials;
  private final AuthorizationRevocationPublisher revocations;
  private final Clock clock;
  private final PermissionRevocationSla sla;

  public IdentityLifecycleService(
      UserAccountRepository accounts,
      UserSessionRepository sessions,
      RefreshCredentialRevoker refreshCredentials,
      AuthorizationRevocationPublisher revocations,
      Clock clock) {
    this(accounts, sessions, refreshCredentials, revocations, clock, PermissionRevocationSla.p1());
  }

  public IdentityLifecycleService(
      UserAccountRepository accounts,
      UserSessionRepository sessions,
      RefreshCredentialRevoker refreshCredentials,
      AuthorizationRevocationPublisher revocations,
      Clock clock,
      PermissionRevocationSla sla) {
    this.accounts = accounts;
    this.sessions = sessions;
    this.refreshCredentials = refreshCredentials;
    this.revocations = revocations;
    this.clock = clock;
    this.sla = sla;
  }

  public UserAccount enable(UUID userId) {
    var now = clock.instant();
    var account = requireAccount(userId);
    if (account.status() == UserAccount.Status.DISABLED) {
      throw new IllegalStateException("已停用用户需要重新邀请，不能直接启用");
    }
    return accounts.save(account.activate(now));
  }

  public UserAccount suspend(UUID userId, String reason) {
    var now = clock.instant();
    var account = accounts.save(requireAccount(userId).suspend(now, reason));
    revokeSessions(account, reason, now);
    return account;
  }

  public UserAccount disable(UUID userId, String reason) {
    var now = clock.instant();
    var account = accounts.save(requireAccount(userId).disable(now, reason));
    revokeSessions(account, reason, now);
    return account;
  }

  public UserAccount leave(UUID userId, String reason) {
    return disable(userId, "离职：" + reason);
  }

  public UserAccount revokeAuthorization(UUID userId, String reason) {
    var now = clock.instant();
    var account = accounts.save(requireAccount(userId).incrementAuthorizationVersion());
    revokeSessions(account, reason, now);
    return account;
  }

  private void revokeSessions(UserAccount account, String reason, java.time.Instant now) {
    for (var session : sessions.findActiveByUserId(account.id())) {
      sessions.save(session.revoke(now, reason));
      if (session.refreshCredentialReference() != null
          && !session.refreshCredentialReference().isBlank()) {
        refreshCredentials.revoke(session.refreshCredentialReference());
      }
    }
    revocations.publish(
        AuthorizationRevoked.create(
            account.id(), account.authorizationVersion(), reason, now, sla));
  }

  private UserAccount requireAccount(UUID userId) {
    return accounts.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
  }
}
