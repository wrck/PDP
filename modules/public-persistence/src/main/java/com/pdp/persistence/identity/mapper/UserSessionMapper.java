package com.pdp.persistence.identity.mapper;

import com.pdp.identity.domain.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 用户会话 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口；所有 SQL 在 {@code resources/mapper/identity/UserSessionMapper.xml} 中声明。
 *
 * <p>撤销语义：{@code revokeAllByUser} 批量撤销指定用户的全部有效会话（{@code revoked_at IS NULL}），
 * 用于离职即时失效；{@code revoke} 单条撤销，要求 revision 乐观锁匹配。
 * 撤销时效由 {@code PermissionRevocationSlaTest} 守护（FR-160 权限撤销时效 ≤ 30 秒）。
 */
@Mapper
public interface UserSessionMapper {

    int insert(UserSession session);

    UserSession selectById(UUID id);

    UserSession selectBySessionTokenHash(String sessionTokenHash);

    /**
     * 批量撤销指定用户的全部有效会话。
     *
     * <p>{@code expectedRevision} 参数为 0 时跳过乐观锁检查（用于离职/管理员强制下线），
     * 否则要求会话当前 revision 与期望值匹配。
     *
     * @return 受影响行数（撤销的会话数）
     */
    int revokeAllByUser(@Param("userId") UUID userId,
                        @Param("reason") String reason,
                        @Param("now") Instant now,
                        @Param("expectedRevision") int expectedRevision);

    /**
     * 撤销单个会话（乐观锁）。
     *
     * @return 1=成功；0=版本冲突或不存在
     */
    int revoke(@Param("id") UUID id,
               @Param("reason") String reason,
               @Param("now") Instant now,
               @Param("expectedRevision") int expectedRevision);

    /**
     * 查询指定用户的全部有效会话（{@code revoked_at IS NULL AND expires_at > now}）。
     */
    List<UserSession> selectActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
