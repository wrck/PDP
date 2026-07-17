package com.pdp.identity.infrastructure.oidc;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserStatus;
import com.pdp.identity.port.ExternalIdentityRepository;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.shared.id.UuidV7Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OIDC 登录、回调、用户同步与外部身份绑定适配器。
 *
 * <p>OIDC 回调成功后，按 issuer+subject 查找已绑定外部身份；
 * 未绑定时按 IdP claims 创建或匹配用户账户并绑定外部身份。
 * 已停用/离职用户拒绝登录。
 */
@Component
public class OidcUserSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(OidcUserSyncAdapter.class);

    private final ExternalIdentityRepository externalIdentityRepository;
    private final UserAccountRepository userAccountRepository;

    public OidcUserSyncAdapter(ExternalIdentityRepository externalIdentityRepository,
                               UserAccountRepository userAccountRepository) {
        this.externalIdentityRepository = externalIdentityRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 同步 OIDC 用户。返回 PDP 用户账户；停用/离职用户抛出异常拒绝登录。
     *
     * @param issuer       OIDC issuer
     * @param subject      OIDC subject
     * @param providerName IdP 显示名
     * @param claims       OIDC claims（preferred_username、email、name 等）
     */
    public UserAccount syncUser(String issuer, String subject, String providerName, Map<String, Object> claims) {
        // 1. 查找已绑定外部身份
        Optional<ExternalIdentity> existing = externalIdentityRepository.findByIssuerAndSubject(issuer, subject);
        if (existing.isPresent()) {
            UserAccount account = userAccountRepository.findById(existing.get().userId())
                    .orElseThrow(() -> new IllegalStateException("外部身份绑定用户不存在: " + existing.get().userId()));
            assertLoginAllowed(account);
            return account;
        }

        // 2. 未绑定：按 email 匹配或创建用户
        String email = stringClaim(claims, "email");
        String preferredUsername = stringClaim(claims, "preferred_username");
        String displayName = stringClaim(claims, "name");

        UserAccount account;
        if (email != null) {
            Optional<UserAccount> byEmail = userAccountRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                account = byEmail.get();
                assertLoginAllowed(account);
            } else {
                account = createAccount(preferredUsername, displayName, email);
            }
        } else {
            account = createAccount(preferredUsername != null ? preferredUsername : subject, displayName, null);
        }

        // 3. 绑定外部身份
        ExternalIdentity binding = new ExternalIdentity(
                UuidV7Generator.next(),
                account.id(),
                issuer,
                subject,
                providerName,
                Instant.now(),
                Instant.now(),
                1);
        externalIdentityRepository.save(binding);
        log.info("OIDC 用户绑定: issuer={} subject={} userId={}", issuer, subject, account.id());
        return account;
    }

    private UserAccount createAccount(String username, String displayName, String email) {
        UUID id = UuidV7Generator.next();
        String name = username != null ? username : "user-" + id.toString().substring(0, 8);
        UserAccount account = new UserAccount(
                id, name,
                displayName != null ? displayName : name,
                email,
                UserStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                1);
        userAccountRepository.save(account);
        log.info("OIDC 创建新用户: userId={} username={}", id, name);
        return account;
    }

    private void assertLoginAllowed(UserAccount account) {
        if (account.status() != UserStatus.ACTIVE) {
            throw new com.pdp.shared.error.ForbiddenException(
                    "用户状态不允许登录: " + account.status());
        }
    }

    private static String stringClaim(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : v.toString();
    }
}
