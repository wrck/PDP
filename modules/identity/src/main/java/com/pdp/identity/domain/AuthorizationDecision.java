package com.pdp.identity.domain;

import java.util.Set;
import java.util.UUID;

/**
 * 授权决策上下文。
 *
 * <p>聚合操作者、目标工作空间、请求的对象与操作，供 {@code AuthorizationService} 判定。
 */
public record AuthorizationDecision(
        UUID userId,
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String permission,
        DecisionResult result,
        Set<String> matchedRoles,
        String reason) {

    public enum DecisionResult {
        ALLOW,
        DENY_DEFAULT,
        DENY_NO_PERMISSION,
        DENY_WORKSPACE_BOUNDARY,
        DENY_OBJECT_NOT_FOUND
    }

    public boolean isAllowed() {
        return result == DecisionResult.ALLOW;
    }
}
