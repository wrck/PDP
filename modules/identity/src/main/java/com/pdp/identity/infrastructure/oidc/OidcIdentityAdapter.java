package com.pdp.identity.infrastructure.oidc;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.port.ExternalIdentityRepository;
import com.pdp.identity.port.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;

/** OIDC 登录、回调、用户同步与外部身份绑定适配器。 */
public final class OidcIdentityAdapter {
  private final OidcIdentityClient client;
  private final UserAccountRepository accounts;
  private final ExternalIdentityRepository identities;
  private final Clock clock;

  public OidcIdentityAdapter(
      OidcIdentityClient client,
      UserAccountRepository accounts,
      ExternalIdentityRepository identities,
      Clock clock) {
    this.client = client;
    this.accounts = accounts;
    this.identities = identities;
    this.clock = clock;
  }

  public String beginLogin(OidcLoginState state) {
    return client.authorizationUri(state);
  }

  public OidcLoginResult handleCallback(
      OidcCallbackRequest request, OidcLoginState expectedState) {
    if (!constantTimeEquals(request.state(), expectedState.state())) {
      throw new OidcAuthenticationException("OIDC state 校验失败");
    }
    OidcClaims claims = client.exchangeAndVerify(request);
    UserAccount account = synchronizeUser(claims);
    ExternalIdentity identity = bindExternalIdentity(account, claims);
    return new OidcLoginResult(
        account, identity, claims.identityProviderSessionId(), expectedState.redirectUri());
  }

  public UserAccount synchronizeUser(OidcClaims claims) {
    var now = clock.instant();
    var existingIdentity =
        identities.findByIssuerAndSubject(claims.issuer(), claims.subject());
    if (existingIdentity.isPresent()) {
      var account =
          accounts
              .findById(existingIdentity.get().userId())
              .orElseThrow(() -> new OidcAuthenticationException("外部身份绑定的平台账户不存在"));
      var synchronizedAccount =
          accounts.save(account.synchronize(claims.displayName(), claims.email(), now));
      identities.save(
          existingIdentity.get().synchronize(claims.email(), claims.emailVerified(), now));
      return synchronizedAccount;
    }
    var account =
        UserAccount.invited(
                UUID.randomUUID(), claims.subject(), claims.displayName(), claims.email())
            .activate(now);
    return accounts.save(account);
  }

  public ExternalIdentity bindExternalIdentity(UserAccount account, OidcClaims claims) {
    var existing = identities.findByIssuerAndSubject(claims.issuer(), claims.subject());
    if (existing.isPresent() && !existing.get().userId().equals(account.id())) {
      throw new OidcAuthenticationException("外部身份已绑定其他平台账户");
    }
    return existing.orElseGet(
        () ->
            identities.save(
                ExternalIdentity.bind(
                    account.id(),
                    claims.issuer(),
                    claims.subject(),
                    claims.email(),
                    claims.emailVerified(),
                    clock.instant())));
  }

  private static boolean constantTimeEquals(String left, String right) {
    return java.security.MessageDigest.isEqual(
        left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
