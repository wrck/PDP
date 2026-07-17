package com.pdp.identity.port;

import com.pdp.identity.domain.AuthorizationDecision;

import java.util.UUID;

/**
 * 授权决策与资源范围校验端口。
 *
 * <p>系统默认拒绝访问；无权与不存在不得通过响应差异泄露。
 */
public interface AuthorizationPort {

    /** 判定用户在工作空间内对指定对象类型与操作是否有权限。 */
    AuthorizationDecision decide(UUID userId, UUID workspaceId, String objectType, UUID objectId, String permission);

    /** 判定用户在工作空间内是否有指定权限（不绑定具体对象）。 */
    AuthorizationDecision decideWorkspaceScope(UUID userId, UUID workspaceId, String permission);

    /** 校验用户对目标对象的访问范围（数据范围过滤）。 */
    boolean canAccessObject(UUID userId, UUID workspaceId, String objectType, UUID objectId);
}
