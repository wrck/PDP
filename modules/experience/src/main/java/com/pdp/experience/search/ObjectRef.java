package com.pdp.experience.search;

import com.pdp.shared.context.WorkspaceId;

import java.util.Objects;
import java.util.UUID;

/**
 * 业务对象引用值对象（搜索投影用）。
 *
 * <p>对应数据模型 {@code ObjectRef}，由工作空间、对象类型稳定键和对象 ID 组成。
 * 搜索投影、权限过滤和稳定排序均基于此引用定位源对象；打开搜索结果时 MUST 按此引用回查主库，
 * 再次校验当前权限（FR/US14）。
 *
 * <p>对象类型使用 {@link SearchObjectType#stableKey()} 字符串键持久化，禁止依赖枚举序号。
 *
 * @param workspaceId  所属工作空间（权限隔离边界）
 * @param objectType   对象类型
 * @param objectId     对象 ID（UUIDv7）
 */
public record ObjectRef(WorkspaceId workspaceId, SearchObjectType objectType, UUID objectId) {

    public ObjectRef {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(objectType, "objectType 不能为 null");
        Objects.requireNonNull(objectId, "objectId 不能为 null");
    }

    public static ObjectRef of(WorkspaceId workspaceId, SearchObjectType objectType, UUID objectId) {
        return new ObjectRef(workspaceId, objectType, objectId);
    }

    /** 持久化用稳定键表示（不包含 workspaceId，避免跨列耦合）。 */
    public String objectTypeKey() {
        return objectType.stableKey();
    }
}
