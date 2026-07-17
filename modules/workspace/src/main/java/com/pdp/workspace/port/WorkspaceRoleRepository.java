package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.RoleStatus;
import com.pdp.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间角色仓储端口。
 *
 * <p>权限键集合以 JSON 文档存储；系统角色（{@code isSystem=true}）由创建时固化，不可删除。
 */
public interface WorkspaceRoleRepository {

    Optional<WorkspaceRole> findById(UUID id);

    Optional<WorkspaceRole> findByKey(UUID workspaceId, String key);

    /**
     * 按工作空间分页查询角色。
     *
     * @param includeSystem 是否包含系统角色
     */
    PageResult<WorkspaceRole> findByWorkspace(UUID workspaceId, boolean includeSystem, PageRequest pageRequest);

    void save(WorkspaceRole role);

    /**
     * 更新角色名称、描述、权限与数据范围类型，并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean update(UUID id, String name, String description, List<String> permissions,
                   DataScopeType dataScopeType, int expectedRevision, Instant now);

    /**
     * 更新角色状态并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, RoleStatus newStatus, int expectedRevision, Instant now);
}
