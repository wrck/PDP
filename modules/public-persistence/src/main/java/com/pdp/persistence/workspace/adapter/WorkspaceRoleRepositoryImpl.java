package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.WorkspaceRoleMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.RoleStatus;
import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.port.WorkspaceRoleRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间角色仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link WorkspaceRoleRepository} 端口，委托 {@link WorkspaceRoleMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>权限键集合以 JSON 文本列存储；适配器在装配 {@link WorkspaceRole} 时通过
 * {@link WorkspaceJsonCodec#readStringList(String)} 反序列化，在持久化时通过
 * {@link WorkspaceJsonCodec#writeStringList(java.util.List)} 序列化。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class WorkspaceRoleRepositoryImpl implements WorkspaceRoleRepository {

    private final WorkspaceRoleMapper mapper;

    public WorkspaceRoleRepositoryImpl(WorkspaceRoleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<WorkspaceRole> findById(UUID id) {
        WorkspaceRoleRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<WorkspaceRole> findByKey(UUID workspaceId, String key) {
        WorkspaceRoleRow row = mapper.selectByWorkspaceAndKey(workspaceId, key);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<WorkspaceRole> findByWorkspace(UUID workspaceId, boolean includeSystem,
                                                     PageRequest pageRequest) {
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<WorkspaceRoleRow> rows = mapper.selectByWorkspace(
                workspaceId, includeSystem, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<WorkspaceRoleRow> pageRows = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        List<WorkspaceRole> page = new ArrayList<>(pageRows.size());
        for (WorkspaceRoleRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public void save(WorkspaceRole role) {
        WorkspaceRoleRow row = new WorkspaceRoleRow(
                role.id(),
                role.workspaceId(),
                role.key(),
                role.name(),
                role.description(),
                WorkspaceJsonCodec.writeStringList(role.permissions()),
                role.dataScopeType(),
                role.status(),
                role.isSystem(),
                role.revision(),
                role.createdAt(),
                role.updatedAt());
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("工作空间角色插入失败: " + role.id());
        }
    }

    @Override
    public boolean update(UUID id, String name, String description, List<String> permissions,
                          DataScopeType dataScopeType, int expectedRevision, Instant now) {
        String permissionsJson = WorkspaceJsonCodec.writeStringList(permissions);
        return mapper.update(id, name, description, permissionsJson, dataScopeType,
                expectedRevision, now) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, RoleStatus newStatus, int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 装配 {@link WorkspaceRole}：从行加载标量字段 + 反序列化权限键集合。
     */
    private WorkspaceRole assemble(WorkspaceRoleRow row) {
        return new WorkspaceRole(
                row.id(),
                row.workspaceId(),
                row.key(),
                row.name(),
                row.description(),
                WorkspaceJsonCodec.readStringList(row.permissionsJson()),
                row.dataScopeType(),
                row.status(),
                row.isSystem(),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }
}
