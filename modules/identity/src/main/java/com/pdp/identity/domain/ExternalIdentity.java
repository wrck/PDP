package com.pdp.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 外部身份绑定领域模型。
 *
 * <p>映射 OIDC 提供者（issuer）与外部 subject 到 PDP 用户账户。
 * 同一用户可绑定多个外部身份（多 IdP），同一外部身份只能绑定一个用户。
 */
public record ExternalIdentity(
        UUID id,
        UUID userId,
        String issuer,
        String subject,
        String providerName,
        Instant boundAt,
        Instant lastSyncedAt,
        int revision) {

    public ExternalIdentity {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为 null");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer 不能为空");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject 不能为空");
        }
    }
}
