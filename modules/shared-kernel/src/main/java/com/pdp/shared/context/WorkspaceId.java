package com.pdp.shared.context;

import com.pdp.shared.id.UuidV7Generator;

import java.util.UUID;

/**
 * 工作空间标识值对象。
 *
 * <p>工作空间是身份、权限、配置和业务数据的隔离边界。所有写命令必须携带工作空间上下文，
 * 跨空间访问必须命中有效协作授权。
 *
 * <p>对应 OpenAPI 头 {@code X-Workspace-Id} 与数据库列 {@code workspace_id}。
 */
public record WorkspaceId(UUID value) {

    public WorkspaceId {
        if (value == null) {
            throw new IllegalArgumentException("WorkspaceId 不能为 null");
        }
    }

    /** 生成新的 UUIDv7 工作空间标识。 */
    public static WorkspaceId next() {
        return new WorkspaceId(UuidV7Generator.next());
    }

    public static WorkspaceId of(UUID value) {
        return new WorkspaceId(value);
    }

    public static WorkspaceId of(String value) {
        return new WorkspaceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
