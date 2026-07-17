package com.pdp.persistence.workspace.adapter;

import com.pdp.workspace.domain.GrantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 跨工作空间协作授权持久化行。
 *
 * <p>允许动作列表以 JSON 文本列存储为 {@code allowedActionsJson}，由适配器在装配
 * {@link com.pdp.workspace.domain.CollaborationGrant} 时反序列化为 {@code List<String>}。
 */
public record CollaborationGrantRow(
        UUID id,
        UUID workspaceId,
        UUID collaboratorWorkspaceId,
        String targetObjectType,
        UUID targetObjectId,
        UUID roleId,
        String allowedActionsJson,
        Instant validUntil,
        GrantStatus status,
        String reason,
        Instant revokedAt,
        String revokeReason,
        int revision,
        Instant createdAt,
        Instant updatedAt) {
}
