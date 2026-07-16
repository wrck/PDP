package com.pdp.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserSession;
import com.pdp.identity.port.AuthorizationPolicyPort;
import com.pdp.identity.port.AuthorizationRevocationPublisher;
import com.pdp.identity.port.ExternalIdentityRepository;
import com.pdp.identity.port.RefreshCredentialRevoker;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.identity.port.UserSessionRepository;
import com.pdp.shared.context.ActorId;
import com.pdp.shared.context.WorkspaceId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityFoundationTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 停用用户应同步提升授权版本撤销会话刷新凭据并发布传播期限() {
    var accounts = new AccountMemory();
    var sessions = new SessionMemory();
    var refreshRevocations = new ArrayList<String>();
    var events = new ArrayList<AuthorizationRevoked>();
    var user = UserAccount.invited(UUID.randomUUID(), "oidc-subject", "用户甲", "a@example.com");
    accounts.save(user.activate(NOW));
    sessions.save(
        UserSession.active(
            UUID.randomUUID(),
            user.id(),
            "idp-session",
            "refresh-ref",
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            user.authorizationVersion()));
    var service =
        new IdentityLifecycleService(
            accounts,
            sessions,
            refreshRevocations::add,
            events::add,
            CLOCK);

    var disabled = service.disable(user.id(), "用户离职");

    assertThat(disabled.status()).isEqualTo(UserAccount.Status.DISABLED);
    assertThat(disabled.authorizationVersion()).isEqualTo(1);
    assertThat(sessions.findActiveByUserId(user.id())).isEmpty();
    assertThat(refreshRevocations).containsExactly("refresh-ref");
    assertThat(events).singleElement()
        .satisfies(
            event -> {
              assertThat(event.localCacheDeadline()).isEqualTo(NOW.plusSeconds(5));
              assertThat(event.searchProjectionDeadline()).isEqualTo(NOW.plusSeconds(30));
              assertThat(event.realtimeSessionDeadline()).isEqualTo(NOW.plusSeconds(30));
              assertThat(event.sessionRevocationDeadline()).isEqualTo(NOW.plusSeconds(60));
            });
  }

  @Test
  void 授权服务应实时读取账户版本并校验工作空间资源动作和范围() {
    var accounts = new AccountMemory();
    var user = UserAccount.invited(UUID.randomUUID(), "subject", "用户乙", "b@example.com").activate(NOW);
    accounts.save(user);
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    AuthorizationPolicyPort policies =
        request ->
            request.actorId().value().equals(user.id())
                && request.workspaceId().equals(workspaceId)
                && request.action().equals("project.read")
                && request.resource().attributes().get("region").equals("cn-east");
    var service = new AuthorizationService(accounts, policies);

    var allowed =
        service.decide(
            new AuthorizationRequest(
                new ActorId(user.id()),
                workspaceId,
                "project.read",
                new ResourceDescriptor(
                    "project", UUID.randomUUID(), workspaceId, Map.of("region", "cn-east"))));

    assertThat(allowed.allowed()).isTrue();
    accounts.save(user.disable(NOW, "停用"));
    assertThat(
            service
                .decide(
                    new AuthorizationRequest(
                        new ActorId(user.id()),
                        workspaceId,
                        "project.read",
                        new ResourceDescriptor(
                            "project", UUID.randomUUID(), workspaceId, Map.of("region", "cn-east"))))
                .reasonCode())
        .isEqualTo("ACTOR_DISABLED");
  }

  @Test
  void 资源所属工作空间不一致必须默认拒绝() {
    var accounts = new AccountMemory();
    var user = UserAccount.invited(UUID.randomUUID(), "subject", "用户丙", "c@example.com").activate(NOW);
    accounts.save(user);
    var service = new AuthorizationService(accounts, ignored -> true);

    assertThat(
            service
                .decide(
                    new AuthorizationRequest(
                        new ActorId(user.id()),
                        new WorkspaceId(UUID.randomUUID()),
                        "project.read",
                        new ResourceDescriptor(
                            "project",
                            UUID.randomUUID(),
                            new WorkspaceId(UUID.randomUUID()),
                            Map.of())))
                .reasonCode())
        .isEqualTo("RESOURCE_WORKSPACE_MISMATCH");
  }

  private static final class AccountMemory implements UserAccountRepository {
    private final Map<UUID, UserAccount> accounts = new HashMap<>();

    @Override
    public Optional<UserAccount> findById(UUID id) {
      return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Optional<UserAccount> findByExternalSubject(String subject) {
      return accounts.values().stream().filter(account -> account.externalSubject().equals(subject)).findFirst();
    }

    @Override
    public UserAccount save(UserAccount account) {
      accounts.put(account.id(), account);
      return account;
    }
  }

  private static final class SessionMemory implements UserSessionRepository {
    private final Map<UUID, UserSession> sessions = new HashMap<>();

    @Override
    public List<UserSession> findActiveByUserId(UUID userId) {
      return sessions.values().stream()
          .filter(session -> session.userId().equals(userId) && session.status() == UserSession.Status.ACTIVE)
          .toList();
    }

    @Override
    public UserSession save(UserSession session) {
      sessions.put(session.id(), session);
      return session;
    }
  }
}
