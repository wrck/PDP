package com.pdp.persistence.workspace.adapter;

import com.pdp.workspace.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 工作空间成员持久化行（标量字段）。
 *
 * <p>成员的角色与数据范围关联存储在 {@code workspace_member_role} 与
 * {@code workspace_member_data_scope} 关联表，由适配器在装配
 * {@link com.pdp.workspace.domain.WorkspaceMember} 时通过独立查询加载，
 * 避免在 record 构造器上映射集合参数。
 */
public record WorkspaceMemberRow(
        UUID id,
        UUID workspaceId,
        UUID userId,
        UUID organizationId,
        MemberStatus status,
        Instant validUntil,
        int revision,
        Instant createdAt,
        Instant updatedAt) {
}
