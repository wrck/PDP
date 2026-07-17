package com.pdp.identity.port;

import com.pdp.identity.domain.UserSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 用户会话仓储端口。
 *
 * <p>支持会话查询、撤销与刷新凭据撤销。撤销时效基线由 {@code PermissionRevocationSlaTest} 守护。
 */
public interface UserSessionRepository {

    void save(UserSession session);

    java.util.Optional<UserSession> findById(UUID id);

    java.util.Optional<UserSession> findBySessionTokenHash(String sessionTokenHash);

    /** 撤销指定用户的全部有效会话（离职即时失效）。返回撤销数量。 */
    int revokeAllByUser(UUID userId, String reason, Instant now, int expectedRevision);

    /** 撤销单个会话。返回是否成功。 */
    boolean revoke(UUID sessionId, String reason, Instant now, int expectedRevision);

    List<UserSession> findActiveByUser(UUID userId, Instant now);
}
