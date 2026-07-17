package com.pdp.workspace.domain;

/**
 * 工作空间成员状态。
 *
 * <p>状态机：
 * <ul>
 *   <li>加入即 {@link #ACTIVE}；</li>
 *   <li>{@link #ACTIVE} → {@link #SUSPENDED}（暂停）；</li>
 *   <li>{@link #SUSPENDED} → {@link #ACTIVE}（恢复）；</li>
 *   <li>任意 → {@link #REMOVED}（移除，FR-068 触发即时撤权）。</li>
 * </ul>
 */
public enum MemberStatus {
    ACTIVE,
    SUSPENDED,
    REMOVED
}
