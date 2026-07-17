package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 跨工作空间协作授权（FR-006）。
 *
 * <p>授权方工作空间 {@code workspaceId} 将指定目标对象（{@code targetObjectType/targetObjectId}）
 * 的部分操作权限授予协作方工作空间 {@code collaboratorWorkspaceId}，按 {@code roleId} 与
 * {@code allowedActions} 限定。授权可撤销（{@link GrantStatus#REVOKED}），
 * 到期自动失效（{@link GrantStatus#EXPIRED}）。
 */
public record CollaborationGrant(
        UUID id,
        UUID workspaceId,
        UUID collaboratorWorkspaceId,
        String targetObjectType,
        UUID targetObjectId,
        UUID roleId,
        List<String> allowedActions,
        Instant validUntil,
        GrantStatus status,
        String reason,
        Instant revokedAt,
        String revokeReason,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public CollaborationGrant {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        if (collaboratorWorkspaceId == null) {
            throw new IllegalArgumentException("collaboratorWorkspaceId 不能为 null");
        }
        if (workspaceId.equals(collaboratorWorkspaceId)) {
            throw new IllegalArgumentException("协作方工作空间不能与授权方相同");
        }
        if (targetObjectType == null || targetObjectType.isBlank()) {
            throw new IllegalArgumentException("targetObjectType 不能为空");
        }
        if (targetObjectId == null) {
            throw new IllegalArgumentException("targetObjectId 不能为 null");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("roleId 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public boolean isActive() {
        return status == GrantStatus.ACTIVE;
    }

    public boolean canRevoke() {
        return status == GrantStatus.ACTIVE;
    }

    /** 判断是否已过有效期（validUntil 非空且早于 now）。 */
    public boolean isExpired(Instant now) {
        return validUntil != null && now.isAfter(validUntil);
    }
}
