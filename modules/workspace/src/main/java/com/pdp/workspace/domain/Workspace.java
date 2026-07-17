package com.pdp.workspace.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 工作空间聚合根（FR-003、FR-004）。
 *
 * <p>工作空间是身份、权限、配置与业务数据的隔离边界。用户可加入多个工作空间，
 * 通过 {@code X-Workspace-Id} 头切换当前上下文。每个工作空间独立维护成员、角色、
 * 组织、数据范围与协作授权。
 *
 * <p>状态机由 {@link WorkspaceStatus} 文档定义；本记录提供迁移前置条件校验，
 * 实际持久化与版本控制由应用服务通过仓储端口执行。
 */
public record Workspace(
        UUID id,
        String code,
        String name,
        String description,
        WorkspaceStatus status,
        UUID ownerUserId,
        String defaultLocale,
        String defaultTimezone,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public Workspace {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId 不能为 null");
        }
        if (defaultLocale == null || defaultLocale.isBlank()) {
            defaultLocale = "zh-CN";
        }
        if (defaultTimezone == null || defaultTimezone.isBlank()) {
            defaultTimezone = "Asia/Shanghai";
        }
    }

    /** DRAFT/SUSPENDED → ACTIVE 是否合法。 */
    public boolean canActivate() {
        return status == WorkspaceStatus.DRAFT || status == WorkspaceStatus.SUSPENDED;
    }

    /** ACTIVE → SUSPENDED 是否合法。 */
    public boolean canSuspend() {
        return status == WorkspaceStatus.ACTIVE;
    }

    /** ACTIVE/SUSPENDED → ARCHIVED 是否合法。 */
    public boolean canArchive() {
        return status == WorkspaceStatus.ACTIVE || status == WorkspaceStatus.SUSPENDED;
    }

    /** ARCHIVED → SUSPENDED（恢复）是否合法。 */
    public boolean canRestore() {
        return status == WorkspaceStatus.ARCHIVED;
    }

    public boolean isActive() {
        return status == WorkspaceStatus.ACTIVE;
    }
}
