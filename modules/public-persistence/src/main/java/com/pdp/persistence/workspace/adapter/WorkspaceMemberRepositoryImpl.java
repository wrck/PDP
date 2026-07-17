package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.WorkspaceMemberMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.MemberStatus;
import com.pdp.workspace.domain.WorkspaceMember;
import com.pdp.workspace.port.MemberQueryFilter;
import com.pdp.workspace.port.WorkspaceMemberRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间成员仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link WorkspaceMemberRepository} 端口，委托 {@link WorkspaceMemberMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>成员标量字段以 {@link WorkspaceMemberRow} 返回；适配器在装配
 * {@link WorkspaceMember} 时通过独立查询加载角色与数据范围 ID 列表。
 *
 * <p>关联表维护：{@link #save} 与 {@link #update} 同步维护
 * {@code workspace_member_role} 与 {@code workspace_member_data_scope} 关联记录。
 * 策略：先 DELETE 旧关联，再批量 INSERT 新关联（同事务原子完成）。
 *
 * <p>FR-068：{@link #revokeAllByUser} 单条 UPDATE 原子完成批量撤权，1 分钟内生效。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class WorkspaceMemberRepositoryImpl implements WorkspaceMemberRepository {

    private final WorkspaceMemberMapper mapper;

    public WorkspaceMemberRepositoryImpl(WorkspaceMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<WorkspaceMember> findById(UUID id) {
        WorkspaceMemberRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<WorkspaceMember> findByWorkspaceAndUser(UUID workspaceId, UUID userId) {
        WorkspaceMemberRow row = mapper.selectByWorkspaceAndUser(workspaceId, userId);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<WorkspaceMember> findByWorkspace(UUID workspaceId, MemberQueryFilter filter,
                                                        PageRequest pageRequest) {
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<WorkspaceMemberRow> rows = mapper.selectByWorkspace(
                workspaceId,
                filter == null ? null : filter.organizationId(),
                filter == null ? null : filter.roleId(),
                filter == null ? null : filter.status(),
                lastId,
                querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<WorkspaceMemberRow> pageRows = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        List<WorkspaceMember> page = new ArrayList<>(pageRows.size());
        for (WorkspaceMemberRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    @Transactional
    public void save(WorkspaceMember member) {
        WorkspaceMemberRow row = new WorkspaceMemberRow(
                member.id(),
                member.workspaceId(),
                member.userId(),
                member.organizationId(),
                member.status(),
                member.validUntil(),
                member.revision(),
                member.createdAt(),
                member.updatedAt());
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("工作空间成员插入失败: " + member.id());
        }
        replaceRoleAssociations(member.id(), member.roleIds());
        replaceDataScopeAssociations(member.id(), member.dataScopeIds());
    }

    @Override
    @Transactional
    public boolean update(UUID id, List<UUID> roleIds, UUID organizationId, List<UUID> dataScopeIds,
                          Instant validUntil, int expectedRevision, Instant now) {
        int affected = mapper.updateAssociations(id, organizationId, validUntil, expectedRevision, now);
        if (affected != 1) {
            return false;
        }
        replaceRoleAssociations(id, roleIds == null ? List.of() : roleIds);
        replaceDataScopeAssociations(id, dataScopeIds == null ? List.of() : dataScopeIds);
        return true;
    }

    @Override
    public boolean updateStatus(UUID id, MemberStatus newStatus, int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    @Override
    public int revokeAllByUser(UUID userId, String reason, Instant now) {
        return mapper.revokeAllByUser(userId, reason, now);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 装配 {@link WorkspaceMember}：加载成员标量字段 + 角色 ID 列表 + 数据范围 ID 列表。
     */
    private WorkspaceMember assemble(WorkspaceMemberRow row) {
        List<UUID> roleIds = mapper.selectRoleIds(row.id());
        List<UUID> dataScopeIds = mapper.selectDataScopeIds(row.id());
        return new WorkspaceMember(
                row.id(),
                row.workspaceId(),
                row.userId(),
                row.organizationId(),
                roleIds,
                dataScopeIds,
                row.status(),
                row.validUntil(),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }

    /**
     * 替换成员的角色关联：先 DELETE 旧关联，再批量 INSERT 新关联。
     * 空列表只 DELETE，不 INSERT。
     */
    private void replaceRoleAssociations(UUID memberId, List<UUID> roleIds) {
        mapper.deleteRoleAssociations(memberId);
        if (roleIds != null && !roleIds.isEmpty()) {
            mapper.insertRoleAssociations(memberId, roleIds);
        }
    }

    /**
     * 替换成员的数据范围关联：先 DELETE 旧关联，再批量 INSERT 新关联。
     */
    private void replaceDataScopeAssociations(UUID memberId, List<UUID> scopeIds) {
        mapper.deleteDataScopeAssociations(memberId);
        if (scopeIds != null && !scopeIds.isEmpty()) {
            mapper.insertDataScopeAssociations(memberId, scopeIds);
        }
    }
}
