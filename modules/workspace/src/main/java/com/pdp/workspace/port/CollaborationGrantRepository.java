package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.GrantStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 跨工作空间协作授权仓储端口（FR-006）。
 *
 * <p>授权方向通过 {@link GrantDirection} 区分：
 * {@link GrantDirection#OUTGOING} 按 {@code workspace_id}（授权方）查询；
 * {@link GrantDirection#INCOMING} 按 {@code collaborator_workspace_id}（被授权方）查询。
 */
public interface CollaborationGrantRepository {

    Optional<CollaborationGrant> findById(UUID id);

    /**
     * 按工作空间分页查询授权。
     *
     * @param direction 授权方向
     * @param status    状态过滤；{@code null} 表示不过滤
     */
    PageResult<CollaborationGrant> findByWorkspace(UUID workspaceId, GrantDirection direction,
                                                   GrantStatus status, PageRequest pageRequest);

    void save(CollaborationGrant grant);

    /**
     * 撤销授权（ACTIVE → REVOKED），记录撤销原因与时间，并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean revoke(UUID id, String reason, Instant now, int expectedRevision);

    /**
     * 将所有已过有效期（{@code valid_until < now}）的 ACTIVE 授权迁移为 EXPIRED。
     *
     * @param now 当前时间
     * @return 受影响行数
     */
    int expireDue(Instant now);
}
