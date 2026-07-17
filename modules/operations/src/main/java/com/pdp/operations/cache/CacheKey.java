package com.pdp.operations.cache;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 缓存键值对象（工作空间隔离 + 类型化命名空间）。
 *
 * <p>对应 spec.md FR-067（缓存使用一致权限）和 FR-124（本地权限缓存最长 5 秒失效）。
 * 所有缓存键 MUST 以工作空间为前缀，防止跨工作空间数据泄露。
 *
 * <p><strong>键结构</strong>：{@code pdp:{workspaceId}:{namespace}:{key}}
 * <ul>
 *   <li>{@code pdp}：平台统一前缀，便于 Redis 客户端筛选和运维清理；</li>
 *   <li>{@code workspaceId}：工作空间隔离边界，跨空间协作数据使用专用命名空间；</li>
 *   <li>{@code namespace}：业务命名空间（如 {@code permission}、{@code view}、{@code config}）；</li>
 *   <li>{@code key}：业务键（如 {@code userId:permission:objectType:objectId}）。</li>
 * </ul>
 *
 * <p><strong>不可变</strong>：缓存键构造后不可修改，可作为 Map 键使用。
 */
public final class CacheKey {

    private static final String PLATFORM_PREFIX = "pdp";

    private final String canonicalForm;
    private final WorkspaceId workspaceId;
    private final String namespace;

    private CacheKey(String canonicalForm, WorkspaceId workspaceId, String namespace) {
        this.canonicalForm = canonicalForm;
        this.workspaceId = workspaceId;
        this.namespace = namespace;
    }

    /**
     * 构造工作空间隔离的缓存键。
     *
     * @param workspaceId 工作空间（null 表示平台级缓存，如全局配置）
     * @param namespace   命名空间（如 {@code permission}、{@code view}）
     * @param key         业务键
     */
    public static CacheKey of(WorkspaceId workspaceId, String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace 不能为 null");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace 不能为空白");
        }
        Objects.requireNonNull(key, "key 不能为 null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空白");
        }
        StringBuilder sb = new StringBuilder(PLATFORM_PREFIX.length()
                + namespace.length() + key.length() + 16);
        sb.append(PLATFORM_PREFIX).append(':');
        if (workspaceId != null) {
            sb.append(workspaceId.value()).append(':');
        }
        sb.append(namespace).append(':').append(key);
        return new CacheKey(sb.toString(), workspaceId, namespace);
    }

    /** 平台级缓存键（无工作空间隔离，如全局配置、字典）。 */
    public static CacheKey platformLevel(String namespace, String key) {
        return of(null, namespace, key);
    }

    /** 权限缓存键。 */
    public static CacheKey forPermission(WorkspaceId workspaceId, UUID userId, String permission) {
        Objects.requireNonNull(userId, "userId 不能为 null");
        Objects.requireNonNull(permission, "permission 不能为 null");
        return of(workspaceId, "permission", userId + ":" + permission);
    }

    /** 权限对象缓存键（对象级权限判定）。 */
    public static CacheKey forPermissionObject(WorkspaceId workspaceId, UUID userId,
                                               String objectType, UUID objectId, String permission) {
        Objects.requireNonNull(userId, "userId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
        Objects.requireNonNull(permission, "permission 不能为 null");
        return of(workspaceId, "permission",
                userId + ":" + objectType + ":" + objectId + ":" + permission);
    }

    /** 视图缓存键（如项目概览、任务列表）。 */
    public static CacheKey forView(WorkspaceId workspaceId, String viewName, String key) {
        return of(workspaceId, "view:" + viewName, key);
    }

    /** 配置缓存键（领域包配置、模板）。 */
    public static CacheKey forConfig(WorkspaceId workspaceId, String configType, String key) {
        return of(workspaceId, "config:" + configType, key);
    }

    /** 规范形式（Redis 键）。 */
    public String canonicalForm() {
        return canonicalForm;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public String namespace() {
        return namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheKey other)) return false;
        return canonicalForm.equals(other.canonicalForm);
    }

    @Override
    public int hashCode() {
        return canonicalForm.hashCode();
    }

    @Override
    public String toString() {
        return canonicalForm;
    }
}
