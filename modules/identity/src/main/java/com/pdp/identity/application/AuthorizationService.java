package com.pdp.identity.application;

import com.pdp.identity.domain.AuthorizationDecision;
import com.pdp.identity.port.AuthorizationPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 统一认证、授权决策和资源范围校验服务。
 *
 * <p>系统默认拒绝访问；无权与不存在不得通过响应差异泄露。
 * 授权决策委托给 {@link AuthorizationPort} 实现（基于角色与数据范围）。
 */
@Service
public class AuthorizationService {

    private final AuthorizationPort authorizationPort;

    public AuthorizationService(AuthorizationPort authorizationPort) {
        this.authorizationPort = authorizationPort;
    }

    /** 判定用户在工作空间内对指定对象的操作权限。 */
    public AuthorizationDecision decide(UUID userId, UUID workspaceId,
                                        String objectType, UUID objectId, String permission) {
        return authorizationPort.decide(userId, workspaceId, objectType, objectId, permission);
    }

    /** 判定用户在工作空间内的权限范围（不绑定具体对象）。 */
    public AuthorizationDecision decideWorkspaceScope(UUID userId, UUID workspaceId, String permission) {
        return authorizationPort.decideWorkspaceScope(userId, workspaceId, permission);
    }

    /** 校验对象访问范围。默认拒绝；无权与不存在统一返回 false。 */
    public boolean canAccessObject(UUID userId, UUID workspaceId, String objectType, UUID objectId) {
        return authorizationPort.canAccessObject(userId, workspaceId, objectType, objectId);
    }

    /** 断言允许，否则抛出 ForbiddenException（无权与不存在统一 404 语义由调用方决定）。 */
    public void assertAllowed(UUID userId, UUID workspaceId,
                              String objectType, UUID objectId, String permission) {
        AuthorizationDecision decision = decide(userId, workspaceId, objectType, objectId, permission);
        if (!decision.isAllowed()) {
            throw new com.pdp.shared.error.ForbiddenException(
                    "权限不足：workspace=" + workspaceId + " object=" + objectType + ":" + objectId
                            + " permission=" + permission + " reason=" + decision.reason());
        }
    }
}
