package com.pdp.identity.infrastructure.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.port.ExternalIdentityRepository;
import com.pdp.identity.port.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OidcIdentityAdapterTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void 回调应验证状态同步账户并绑定外部身份() {
    var accounts = new AccountMemory();
    var identities = new IdentityMemory();
    OidcIdentityClient client =
        request ->
            new OidcClaims(
                "https://idp.example.com",
                "subject-001",
                "session-001",
                "张三",
                "zhangsan@example.com",
                true);
    var adapter = new OidcIdentityAdapter(client, accounts, identities, CLOCK);

    var result =
        adapter.handleCallback(
            new OidcCallbackRequest("auth-code", "state-001"),
            new OidcLoginState("state-001", "nonce-001", "/projects"));

    assertThat(result.account().status()).isEqualTo(UserAccount.Status.ACTIVE);
    assertThat(result.identity().subject()).isEqualTo("subject-001");
    assertThat(result.redirectUri()).isEqualTo("/projects");
  }

  @Test
  void 状态不匹配必须在访问OIDC客户端前失败() {
    var adapter =
        new OidcIdentityAdapter(
            ignored -> { throw new AssertionError("不应访问客户端"); },
            new AccountMemory(),
            new IdentityMemory(),
            CLOCK);

    assertThatThrownBy(
            () ->
                adapter.handleCallback(
                    new OidcCallbackRequest("code", "tampered"),
                    new OidcLoginState("expected", "nonce", "/")))
        .isInstanceOf(OidcAuthenticationException.class)
        .hasMessageContaining("state");
  }

  private static final class AccountMemory implements UserAccountRepository {
    private final Map<UUID, UserAccount> values = new HashMap<>();
    public Optional<UserAccount> findById(UUID id) { return Optional.ofNullable(values.get(id)); }
    public Optional<UserAccount> findByExternalSubject(String subject) {
      return values.values().stream().filter(value -> value.externalSubject().equals(subject)).findFirst();
    }
    public UserAccount save(UserAccount account) { values.put(account.id(), account); return account; }
  }

  private static final class IdentityMemory implements ExternalIdentityRepository {
    private final Map<String, ExternalIdentity> values = new HashMap<>();
    public Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject) {
      return Optional.ofNullable(values.get(issuer + "|" + subject));
    }
    public ExternalIdentity save(ExternalIdentity identity) {
      values.put(identity.issuer() + "|" + identity.subject(), identity); return identity;
    }
  }
}
