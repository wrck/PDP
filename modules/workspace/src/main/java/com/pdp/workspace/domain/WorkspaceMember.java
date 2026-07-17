package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间成员（FR-004、FR-005、FR-068）。
 *
 * <p>用户加入工作空间的归属记录，绑定角色与数据范围。移除/暂停必须即时撤销该用户在此空间的
 * 会话与权限（FR-068，1 分钟内生效）。{@code validUntil} 为可选到期时间。
 */
public record WorkspaceMember(
        UUID id,
        UUID workspaceId,
        UUID userId,
        UUID organizationId,
        List<UUID> roleIds,
        List<UUID> dataScopeIds,
        MemberStatus status,
        Instant validUntil,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public WorkspaceMember {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId 不能为 null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        dataScopeIds = dataScopeIds == null ? List.of() : List.copyOf(dataScopeIds);
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** ACTIVE → SUSPENDED 是否合法。 */
    public boolean canSuspend() {
        return status == MemberStatus.ACTIVE;
    }

    /** SUSPENDED → ACTIVE 是否合法。 */
    public boolean canResume() {
        return status == MemberStatus.SUSPENDED;
    }
}
