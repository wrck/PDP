package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.CollaborationGrantMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.GrantStatus;
import com.pdp.workspace.port.CollaborationGrantRepository;
import com.pdp.workspace.port.GrantDirection;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 跨工作空间协作授权仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link CollaborationGrantRepository} 端口，委托 {@link CollaborationGrantMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>允许动作列表以 JSON 文本列存储；适配器在装配 {@link CollaborationGrant} 时通过
 * {@link WorkspaceJsonCodec#readStringList(String)} 反序列化，在持久化时通过
 * {@link WorkspaceJsonCodec#writeStringList(java.util.List)} 序列化。
 *
 * <p>授权方向：{@link GrantDirection#OUTGOING} 按 {@code workspace_id}（授权方）查询，
 * {@link GrantDirection#INCOMING} 按 {@code collaborator_workspace_id}（被授权方）查询。
 * 适配器将枚举解析为列名常量，通过 {@code ${directionColumn}} 拼接到 SQL，避免 SQL 注入风险。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class CollaborationGrantRepositoryImpl implements CollaborationGrantRepository {

    /** 授权方列名常量（OUTGOING 方向查询使用）。 */
    private static final String COLUMN_WORKSPACE_ID = "workspace_id";
    /** 被授权方列名常量（INCOMING 方向查询使用）。 */
    private static final String COLUMN_COLLABORATOR_WORKSPACE_ID = "collaborator_workspace_id";

    private final CollaborationGrantMapper mapper;

    public CollaborationGrantRepositoryImpl(CollaborationGrantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CollaborationGrant> findById(UUID id) {
        CollaborationGrantRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<CollaborationGrant> findByWorkspace(UUID workspaceId, GrantDirection direction,
                                                          GrantStatus status, PageRequest pageRequest) {
        String directionColumn = resolveDirectionColumn(direction);
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<CollaborationGrantRow> rows = mapper.selectByWorkspace(
                workspaceId, directionColumn, status, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<CollaborationGrantRow> pageRows = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        List<CollaborationGrant> page = new ArrayList<>(pageRows.size());
        for (CollaborationGrantRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public void save(CollaborationGrant grant) {
        CollaborationGrantRow row = new CollaborationGrantRow(
                grant.id(),
                grant.workspaceId(),
                grant.collaboratorWorkspaceId(),
                grant.targetObjectType(),
                grant.targetObjectId(),
                grant.roleId(),
                WorkspaceJsonCodec.writeStringList(grant.allowedActions()),
                grant.validUntil(),
                grant.status(),
                grant.reason(),
                grant.revokedAt(),
                grant.revokeReason(),
                grant.revision(),
                grant.createdAt(),
                grant.updatedAt());
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("协作授权插入失败: " + grant.id());
        }
    }

    @Override
    public boolean revoke(UUID id, String reason, Instant now, int expectedRevision) {
        return mapper.revoke(id, reason, now, expectedRevision) == 1;
    }

    @Override
    public int expireDue(Instant now) {
        return mapper.expireDue(now);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 将 {@link GrantDirection} 解析为列名常量。
     *
     * <p>仅返回固定常量字符串，绝不直接拼接用户输入，避免 SQL 注入。
     */
    private String resolveDirectionColumn(GrantDirection direction) {
        if (direction == null) {
            return COLUMN_WORKSPACE_ID;
        }
        return switch (direction) {
            case OUTGOING -> COLUMN_WORKSPACE_ID;
            case INCOMING -> COLUMN_COLLABORATOR_WORKSPACE_ID;
        };
    }

    /**
     * 装配 {@link CollaborationGrant}：从行加载标量字段 + 反序列化允许动作列表。
     */
    private CollaborationGrant assemble(CollaborationGrantRow row) {
        return new CollaborationGrant(
                row.id(),
                row.workspaceId(),
                row.collaboratorWorkspaceId(),
                row.targetObjectType(),
                row.targetObjectId(),
                row.roleId(),
                WorkspaceJsonCodec.readStringList(row.allowedActionsJson()),
                row.validUntil(),
                row.status(),
                row.reason(),
                row.revokedAt(),
                row.revokeReason(),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }
}
