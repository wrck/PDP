package com.pdp.workspace.domain;

/**
 * 工作空间角色状态。
 *
 * <p>创建即 {@link #ACTIVE}；{@link #ACTIVE} → {@link #DISABLED}（停用）。
 */
public enum RoleStatus {
    ACTIVE,
    DISABLED
}
