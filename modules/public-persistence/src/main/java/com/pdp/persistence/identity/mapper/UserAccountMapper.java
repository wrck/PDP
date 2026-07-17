package com.pdp.persistence.identity.mapper;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 用户账户 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/identity/UserAccountMapper.xml} 中声明；
 * 领域层与应用层不感知此接口（由 {@link com.pdp.identity.port.UserAccountRepository}
 * 适配器实现隔离，符合宪章原则 V）。
 *
 * <p>数据源路由：未声明 {@code @DS}，使用 {@code pdpPrimary} 主库（默认 primary）。
 * 在线业务写路径始终在主库事务内执行，禁止事务内切换（{@code DataSourceRoutingGuard}）。
 */
@Mapper
public interface UserAccountMapper {

    UserAccount selectById(UUID id);

    UserAccount selectByUsername(String username);

    UserAccount selectByEmail(String email);

    List<UserAccount> selectByStatus(@Param("status") UserStatus status, @Param("limit") int limit);

    int insert(UserAccount account);

    /**
     * 更新状态并递增 revision。
     *
     * @param id              用户 ID
     * @param newStatus       新状态
     * @param expectedRevision 期望的旧 revision（乐观锁）
     * @param now             当前时间（用于 updated_at）
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") UserStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);
}
