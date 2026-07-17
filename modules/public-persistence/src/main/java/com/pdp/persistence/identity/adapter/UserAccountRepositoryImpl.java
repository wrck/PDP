package com.pdp.persistence.identity.adapter;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserStatus;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.persistence.identity.mapper.UserAccountMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户账户仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link UserAccountRepository} 端口，委托 {@link UserAccountMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.identity.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>乐观锁：{@link #updateStatus} 通过 SQL {@code WHERE revision = #{expectedRevision}}
 * 与 {@code SET revision = revision + 1} 实现；返回 {@code false} 表示版本冲突或不存在，
 * 调用方（{@code IdentityLifecycleService}）抛出 {@code CONFLICT} 业务异常。
 */
@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final UserAccountMapper mapper;

    public UserAccountRepositoryImpl(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectByUsername(username));
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return Optional.ofNullable(mapper.selectByEmail(email));
    }

    @Override
    public void save(UserAccount account) {
        int rows = mapper.insert(account);
        if (rows != 1) {
            throw new IllegalStateException("用户账户插入失败: " + account.id());
        }
    }

    @Override
    public List<UserAccount> findByStatus(UserStatus status, int limit) {
        return mapper.selectByStatus(status, limit);
    }

    @Override
    public boolean updateStatus(UUID id, UserStatus newStatus, int expectedRevision) {
        return mapper.updateStatus(id, newStatus, expectedRevision, Instant.now()) == 1;
    }
}
