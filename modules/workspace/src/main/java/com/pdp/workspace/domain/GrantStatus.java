package com.pdp.workspace.domain;

/**
 * 跨工作空间协作授权状态。
 *
 * <p>状态机：
 * <ul>
 *   <li>创建即 {@link #ACTIVE}（DRAFT 为预留中间态，创建直接生效）；</li>
 *   <li>{@link #ACTIVE} → {@link #REVOKED}（撤销）；</li>
 *   <li>{@link #ACTIVE} → {@link #EXPIRED}（valid_until 到期自动迁移）。</li>
 * </ul>
 * REVOKED/EXPIRED 为终态。
 */
public enum GrantStatus {
    DRAFT,
    ACTIVE,
    EXPIRED,
    REVOKED
}
