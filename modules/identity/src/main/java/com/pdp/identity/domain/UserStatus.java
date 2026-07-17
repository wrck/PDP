package com.pdp.identity.domain;

/**
 * 用户账户状态。
 *
 * <p>状态机：{@code DRAFT → ACTIVE → DISABLED → DEPARTED}；DISABLED 可恢复为 ACTIVE。
 */
public enum UserStatus {
    DRAFT,
    ACTIVE,
    DISABLED,
    DEPARTED
}
