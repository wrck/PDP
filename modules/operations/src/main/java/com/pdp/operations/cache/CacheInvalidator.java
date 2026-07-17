package com.pdp.operations.cache;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 缓存失效协调器（FR-124 事件驱动批量失效）。
 *
 * <p>对应 spec.md FR-124 和 contracts/events.md 事件映射：
 * <ul>
 *   <li>{@code pdp.workspace.membership.changed}：成员、角色或有效期改变 → 失效权限缓存；</li>
 *   <li>{@code pdp.workspace.collaboration.changed}：跨空间授权生效、到期或撤销 → 失效权限缓存；</li>
 *   <li>{@code pdp.domain-package.published}：领域包版本发布 → 失效配置缓存；</li>
 *   <li>{@code pdp.user.deactivated}/{@code pdp.user.permission.revoked}：用户停用/权限撤销 →
 *       失效该用户所有权限缓存。</li>
 * </ul>
 *
 * <p><strong>SLA 保证</strong>（SC-036）：本地权限缓存 5 秒内失效。本协调器在事件触发时
 * <b>主动</b>失效（即时），不依赖 TTL 自然过期，确保权限撤销后新请求立即使用最新权限。
 *
 * <p><strong>多级缓存协调</strong>：同时失效本地缓存（{@link PermissionCache}）和分布式缓存
 * （{@link CacheGuard}），保证多实例一致性。本地缓存失效后，本实例新请求回源；
 * 分布式缓存失效后，其他实例新请求回源。
 *
 * <p><strong>降级</strong>（FR-106）：失效失败不影响正确性，缓存按 TTL 自然过期。
 */
public class CacheInvalidator {

    /** 权限缓存命名空间。 */
    public static final String NAMESPACE_PERMISSION = "permission";

    /** 视图缓存命名空间前缀。 */
    public static final String NAMESPACE_VIEW_PREFIX = "view:";

    /** 配置缓存命名空间前缀。 */
    public static final String NAMESPACE_CONFIG_PREFIX = "config:";

    private final PermissionCache permissionCache;
    private final CacheGuard distributedCacheGuard;

    public CacheInvalidator(PermissionCache permissionCache, CacheGuard distributedCacheGuard) {
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache 不能为 null");
        this.distributedCacheGuard = Objects.requireNonNull(distributedCacheGuard,
                "distributedCacheGuard 不能为 null");
    }

    /**
     * 工作空间成员变更（{@code pdp.workspace.membership.changed}）。
     *
     * <p>失效该工作空间内所有权限缓存（本地 + 分布式）。
     *
     * @param workspaceId 工作空间
     * @return 失效的本地条目数
     */
    public long onWorkspaceMembershipChanged(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        long localCount = permissionCache.invalidateWorkspace(workspaceId);
        distributedCacheGuard.invalidateByNamespace(workspaceId, NAMESPACE_PERMISSION);
        return localCount;
    }

    /**
     * 跨工作空间协作授权变更（{@code pdp.workspace.collaboration.changed}）。
     *
     * <p>失效源和目标工作空间的权限缓存。
     *
     * @param sourceWorkspaceId 源工作空间
     * @param targetWorkspaceId 目标工作空间
     */
    public void onWorkspaceCollaborationChanged(WorkspaceId sourceWorkspaceId,
                                                 WorkspaceId targetWorkspaceId) {
        Objects.requireNonNull(sourceWorkspaceId, "sourceWorkspaceId 不能为 null");
        Objects.requireNonNull(targetWorkspaceId, "targetWorkspaceId 不能为 null");
        permissionCache.invalidateWorkspace(sourceWorkspaceId);
        permissionCache.invalidateWorkspace(targetWorkspaceId);
        distributedCacheGuard.invalidateByNamespace(sourceWorkspaceId, NAMESPACE_PERMISSION);
        distributedCacheGuard.invalidateByNamespace(targetWorkspaceId, NAMESPACE_PERMISSION);
    }

    /**
     * 用户权限撤销（{@code pdp.user.permission.revoked}，FR-124/FR-068）。
     *
     * <p>失效该用户在所有工作空间的权限缓存。本地缓存按命名空间批量删除，
     * 分布式缓存按命名空间批量删除。
     *
     * @param userId 用户 ID
     */
    public void onUserPermissionRevoked(UUID userId) {
        Objects.requireNonNull(userId, "userId 不能为 null");
        // 本地：删除所有权限缓存（简化为按命名空间删除，因为跨工作空间）
        permissionCache.invalidateAll();
        // 分布式：按权限命名空间批量删除（所有工作空间）
        distributedCacheGuard.invalidateByNamespace(null, NAMESPACE_PERMISSION);
    }

    /**
     * 用户停用（{@code pdp.user.deactivated}，FR-068）。
     *
     * <p>失效该用户所有权限缓存，确保停用后新请求立即拒绝。
     *
     * @param userId 用户 ID
     */
    public void onUserDeactivated(UUID userId) {
        onUserPermissionRevoked(userId);
    }

    /**
     * 领域包发布（{@code pdp.domain-package.published}）。
     *
     * <p>失效配置缓存（模板、领域包配置）。
     *
     * @param workspaceId 工作空间（null 表示平台级配置）
     */
    public void onDomainPackagePublished(WorkspaceId workspaceId) {
        // 本地权限缓存不受影响，仅失效分布式配置缓存
        if (workspaceId != null) {
            distributedCacheGuard.invalidateByNamespace(workspaceId, NAMESPACE_CONFIG_PREFIX + "domain-package");
        } else {
            distributedCacheGuard.invalidateByNamespace(null, NAMESPACE_CONFIG_PREFIX + "domain-package");
        }
    }

    /**
     * 项目变更（{@code pdp.project.created}/{@code pdp.project.lifecycle.changed}）。
     *
     * <p>失效项目相关视图缓存。
     *
     * @param workspaceId 工作空间
     */
    public void onProjectChanged(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        distributedCacheGuard.invalidateByNamespace(workspaceId, NAMESPACE_VIEW_PREFIX + "project");
    }

    /**
     * 任务/交付件变更（视图缓存失效）。
     *
     * @param workspaceId 工作空间
     */
    public void onTaskOrDeliverableChanged(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        distributedCacheGuard.invalidateByNamespace(workspaceId, NAMESPACE_VIEW_PREFIX + "task");
        distributedCacheGuard.invalidateByNamespace(workspaceId, NAMESPACE_VIEW_PREFIX + "deliverable");
    }

    /**
     * 全量失效（权限模型变更、灾备切换等极端场景）。
     *
     * <p>谨慎使用：会导致短时缓存全部穿透。调用后建议预热关键路径。
     */
    public void invalidateAll() {
        permissionCache.invalidateAll();
        distributedCacheGuard.invalidateByNamespace(null, NAMESPACE_PERMISSION);
        distributedCacheGuard.invalidateByNamespace(null, NAMESPACE_VIEW_PREFIX.substring(0,
                NAMESPACE_VIEW_PREFIX.length() - 1));
    }
}
