package com.pdp.workspace.domain;

/**
 * 组织状态。
 *
 * <p>{@link #ACTIVE} 正常；{@link #INACTIVE} 已停用（软删除，保留子组织与历史）。
 */
public enum OrganizationStatus {
    ACTIVE,
    INACTIVE
}
