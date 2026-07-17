package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.Organization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织仓储端口。
 *
 * <p>组织树通过物化路径 {@code path} 与 {@code depth} 维护；移动组织时整体更新路径与深度。
 */
public interface OrganizationRepository {

    Optional<Organization> findById(UUID id);

    /**
     * 按工作空间与父组织分页查询。
     *
     * @param parentId 父组织 ID；{@code null} 表示顶层组织
     */
    PageResult<Organization> findByWorkspaceAndParent(UUID workspaceId, UUID parentId, PageRequest pageRequest);

    void save(Organization organization);

    /**
     * 更新基本信息（名称、描述）并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateBasicInfo(UUID id, String name, String description, int expectedRevision, Instant now);

    /**
     * 更新层级路径、深度与父组织并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updatePath(UUID id, String newPath, int newDepth, UUID newParentId, int expectedRevision, Instant now);

    /**
     * 停用组织（ACTIVE → INACTIVE）并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean deactivate(UUID id, int expectedRevision, Instant now);
}
