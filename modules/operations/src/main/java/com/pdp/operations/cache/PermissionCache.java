package com.pdp.operations.cache;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 权限缓存（FR-124：本地权限缓存最长 5 秒失效，SC-036）。
 *
 * <p>对应 spec.md FR-124："跨工作空间授权的创建、变更、到期和撤销 MUST 在命令成功后立即用于
 * 新的在线请求；本地权限缓存最长 5 秒失效"和 SC-036："本地缓存 5 秒内失效"。
 *
 * <p><strong>核心契约</strong>：
 * <ul>
 *   <li><b>TTL ≤ 5 秒</b>：基于 {@link LocalCache}，TTL 强制为 {@link CacheTtl#PERMISSION_LOCAL_TTL}（5 秒，
 *       无抖动保证 SLA），超出拒绝写入；</li>
 *   <li><b>不作为唯一事实源</b>（spec.md 不变量）：权限缓存仅用于加速判定，所有权限决策 MUST 可
 *       从主库重建。敏感操作（下载、导出、审批）MUST 实时复核权限，不依赖缓存；</li>
 *   <li><b>降级</b>（FR-106）：本地缓存异常时直接回源 {@link AuthorizationLoader}，
 *       不影响核心操作正确性；</li>
 *   <li><b>批量失效</b>：权限撤销时调用 {@link #invalidateWorkspace} 批量失效工作空间内所有权限缓存。</li>
 * </ul>
 *
 * <p><strong>缓存值</strong>：{@link Boolean}（true=允许，false=拒绝）。
 * 权限判定是二元结果，空值（未判定）不缓存，避免缓存"无权"导致短时拒绝合法请求。
 *
 * <p><strong>实时复核</strong>（spec.md）：对象打开、导出和下载始终实时复核权限，
 * 不读取此缓存。此缓存仅用于页面渲染、列表过滤等可容忍 5 秒延迟的场景。
 */
public class PermissionCache {

    private final LocalCache localCache;
    private final CacheTtl permissionTtl;

    public PermissionCache() {
        this(LocalCache.forPermission());
    }

    public PermissionCache(LocalCache localCache) {
        this.localCache = Objects.requireNonNull(localCache, "localCache 不能为 null");
        // FR-124：5 秒 TTL，无抖动保证 SLA
        if (localCache.maxTtl().compareTo(CacheTtl.PERMISSION_LOCAL_TTL) > 0) {
            throw new IllegalArgumentException(
                    "LocalCache maxTtl " + localCache.maxTtl() + " 超过 FR-124 权限缓存上限 "
                            + CacheTtl.PERMISSION_LOCAL_TTL);
        }
        this.permissionTtl = CacheTtl.forPermissionLocal();
    }

    /**
     * 查询工作空间级权限（缓存或加载）。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param permission  权限标识（如 {@code project:read}）
     * @param loader      缓存未命中时从主库加载的判定器
     * @return true=允许；false=拒绝
     */
    public boolean decideWorkspaceScope(WorkspaceId workspaceId, UUID userId,
                                        String permission, Supplier<Boolean> loader) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(userId, "userId 不能为 null");
        Objects.requireNonNull(permission, "permission 不能为 null");
        Objects.requireNonNull(loader, "loader 不能为 null");

        CacheKey key = CacheKey.forPermission(workspaceId, userId, permission);
        return decide(key, loader);
    }

    /**
     * 查询对象级权限（缓存或加载）。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param objectType  对象类型稳定键
     * @param objectId    对象 ID
     * @param permission  权限标识
     * @param loader      缓存未命中时从主库加载的判定器
     * @return true=允许；false=拒绝
     */
    public boolean decideObject(WorkspaceId workspaceId, UUID userId,
                                String objectType, UUID objectId, String permission,
                                Supplier<Boolean> loader) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(userId, "userId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        Objects.requireNonNull(permission, "permission 不能为 null");
        Objects.requireNonNull(loader, "loader 不能为 null");

        CacheKey key = CacheKey.forPermissionObject(workspaceId, userId, objectType, objectId, permission);
        return decide(key, loader);
    }

    /**
     * 失效单个用户的工作空间级权限缓存。
     *
     * <p>权限变更时调用，确保新请求使用最新权限（FR-124：5 秒内失效，
     * 主动失效更快）。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param permission  权限标识（null 表示该用户所有权限）
     */
    public void invalidate(WorkspaceId workspaceId, UUID userId, String permission) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(userId, "userId 不能为 null");
        if (permission != null) {
            localCache.delete(CacheKey.forPermission(workspaceId, userId, permission));
        } else {
            // 批量删除该用户权限缓存（按命名空间 + 用户前缀需扫描，简化为删除整个权限命名空间）
            localCache.deleteByNamespace("permission");
        }
    }

    /**
     * 失效对象级权限缓存。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param objectType  对象类型
     * @param objectId    对象 ID
     * @param permission  权限标识（null 表示该对象所有权限）
     */
    public void invalidateObject(WorkspaceId workspaceId, UUID userId,
                                 String objectType, UUID objectId, String permission) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(userId, "userId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        if (permission != null) {
            localCache.delete(CacheKey.forPermissionObject(
                    workspaceId, userId, objectType, objectId, permission));
        } else {
            localCache.deleteByNamespace("permission");
        }
    }

    /**
     * 失效工作空间内所有权限缓存（工作空间成员变更、跨空间授权变更）。
     *
     * <p>对应 FR-124："跨工作空间授权的创建、变更、到期和撤销 MUST 立即用于新的在线请求"。
     *
     * @param workspaceId 工作空间
     * @return 失效条目数
     */
    public long invalidateWorkspace(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        return localCache.deleteByWorkspace(workspaceId);
    }

    /**
     * 失效所有权限缓存（权限模型变更，谨慎使用）。
     *
     * @return 失效条目数
     */
    public long invalidateAll() {
        long size = localCache.size();
        localCache.invalidateAll();
        return size;
    }

    /** 清理过期条目（定期调用）。 */
    public int cleanup() {
        return localCache.cleanup();
    }

    public long hitCount() {
        return localCache.hitCount();
    }

    public long missCount() {
        return localCache.missCount();
    }

    /** 缓存命中率（0.0~1.0）。 */
    public double hitRate() {
        return localCache.hitRate();
    }

    // ==================== 内部 ====================

    private boolean decide(CacheKey key, Supplier<Boolean> loader) {
        try {
            Optional<Boolean> cached = localCache.get(key, Boolean.class);
            if (cached.isPresent()) {
                return cached.get();
            }
            // 未命中，回源加载
            boolean decision = loader.get();
            // 缓存判定结果（含 false，加速拒绝路径）
            // 注意：null/异常不缓存，避免缓存错误状态
            localCache.put(key, decision, permissionTtl);
            return decision;
        } catch (CacheException e) {
            // TTL 违反或其他缓存异常，降级为直接回源
            return loader.get();
        }
    }
}
