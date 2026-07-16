package com.pdp.shared.context;

import java.util.Set;

/**
 * 操作者上下文值对象。
 *
 * <p>聚合当前请求的操作者身份、所属工作空间与生效权限集合。
 * 由 {@code RequestContextFilter}（apps/api）从认证主体解析并注入到请求上下文。
 * 写命令、权限判定和审计均依赖此上下文。
 */
public record OperatorContext(
        ActorRef actor,
        WorkspaceId workspaceId,
        Set<String> grantedPermissions,
        Set<String> roles) {

    public OperatorContext {
        if (actor == null) {
            throw new IllegalArgumentException("actor 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        grantedPermissions = grantedPermissions == null ? Set.of() : Set.copyOf(grantedPermissions);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** 判断操作者是否拥有指定权限。默认拒绝。 */
    public boolean hasPermission(String permission) {
        return permission != null && grantedPermissions.contains(permission);
    }

    /** 判断操作者是否拥有任一指定权限。 */
    public boolean hasAnyPermission(String... permissions) {
        for (String p : permissions) {
            if (grantedPermissions.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
