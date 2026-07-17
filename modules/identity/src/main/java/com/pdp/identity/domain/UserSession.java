package com.pdp.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户会话领域模型。
 *
 * <p>记录认证会话与刷新凭据，支持会话撤销与离职即时失效。
 */
public record UserSession(
        UUID id,
        UUID userId,
        String sessionTokenHash,
        String refreshTokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        String sourceIp,
        int revision) {

    public UserSession {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为 null");
        }
        if (sessionTokenHash == null || sessionTokenHash.isBlank()) {
            throw new IllegalArgumentException("sessionTokenHash 不能为空");
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt != null && now.isBefore(expiresAt);
    }
}
