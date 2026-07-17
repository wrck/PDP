package com.pdp.identity.port;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户账户仓储端口。
 *
 * <p>领域/应用层依赖此端口，不依赖 MyBatis、MySQL 驱动或持久化记录。
 * 实现位于 public-persistence 基础设施适配器边界。
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    void save(UserAccount account);

    /** 按状态分页查询（游标分页）。 */
    java.util.List<UserAccount> findByStatus(UserStatus status, int limit);

    /** 更新状态并递增 revision；返回是否成功（1=成功，0=版本冲突或不存在）。 */
    boolean updateStatus(UUID id, UserStatus newStatus, int expectedRevision);
}
