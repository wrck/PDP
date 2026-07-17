package com.pdp.persistence.identity.adapter;

import com.pdp.identity.domain.UserSession;
import com.pdp.identity.port.UserSessionRepository;
import com.pdp.persistence.identity.mapper.UserSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户会话仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link UserSessionRepository} 端口，委托 {@link UserSessionMapper}；
 * 离职即时失效由 {@link #revokeAllByUser} 批量撤销全部有效会话（{@code expectedRevision=0}
 * 跳过乐观锁，强制撤销），由 {@code IdentityLifecycleService.depart} 触发。
 *
 * <p>撤销时效基线（FR-160 ≤ 30 秒）由 {@code PermissionRevocationSlaTest} 守护；
 * 本适配器仅保证单次 UPDATE 语句原子完成撤销，传播时效由调用方与异步执行器协同控制。
 */
@Repository
public class UserSessionRepositoryImpl implements UserSessionRepository {

    private final UserSessionMapper mapper;

    public UserSessionRepositoryImpl(UserSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(UserSession session) {
        int rows = mapper.insert(session);
        if (rows != 1) {
            throw new IllegalStateException("用户会话插入失败: " + session.id());
        }
    }

    @Override
    public Optional<UserSession> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<UserSession> findBySessionTokenHash(String sessionTokenHash) {
        return Optional.ofNullable(mapper.selectBySessionTokenHash(sessionTokenHash));
    }

    @Override
    public int revokeAllByUser(UUID userId, String reason, Instant now, int expectedRevision) {
        return mapper.revokeAllByUser(userId, reason, now, expectedRevision);
    }

    @Override
    public boolean revoke(UUID sessionId, String reason, Instant now, int expectedRevision) {
        return mapper.revoke(sessionId, reason, now, expectedRevision) == 1;
    }

    @Override
    public List<UserSession> findActiveByUser(UUID userId, Instant now) {
        return mapper.selectActiveByUser(userId, now);
    }
}
