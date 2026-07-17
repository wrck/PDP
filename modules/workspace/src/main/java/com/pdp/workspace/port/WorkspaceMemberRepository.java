package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.MemberStatus;
import com.pdp.workspace.domain.WorkspaceMember;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间成员仓储端口。
 *
 * <p>成员的角色与数据范围关联存储在独立关联表；{@link #save} 与 {@link #update}
 * 同步维护关联记录。FR-068：移除/暂停成员时通过 {@link #revokeAllByUser}
 * 即时撤销该用户在此空间的全部成员记录（传播至会话失效由应用层协调）。
 */
public interface WorkspaceMemberRepository {

    Optional<WorkspaceMember> findById(UUID id);

    Optional<WorkspaceMember> findByWorkspaceAndUser(UUID workspaceId, UUID userId);

    PageResult<WorkspaceMember> findByWorkspace(UUID workspaceId, MemberQueryFilter filter, PageRequest pageRequest);

    void save(WorkspaceMember member);

    /**
     * 更新成员的角色、组织归属、数据范围与有效期，并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean update(UUID id, List<UUID> roleIds, UUID organizationId, List<UUID> dataScopeIds,
                   Instant validUntil, int expectedRevision, Instant now);

    /**
     * 更新成员状态并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, MemberStatus newStatus, int expectedRevision, Instant now);

    /**
     * 撤销指定用户的全部成员记录（FR-068 即时撤权）。
     * 将所有 ACTIVE/SUSPENDED 成员记录置为 REMOVED 并递增 revision。
     *
     * @param userId 用户 ID
     * @param reason 撤权原因
     * @param now    当前时间
     * @return 受影响行数
     */
    int revokeAllByUser(UUID userId, String reason, Instant now);
}
