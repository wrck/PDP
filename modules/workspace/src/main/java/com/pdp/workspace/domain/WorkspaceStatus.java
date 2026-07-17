package com.pdp.workspace.domain;

/**
 * 工作空间状态。
 *
 * <p>状态机（FR-003）：
 * <ul>
 *   <li>{@link #DRAFT} → {@link #ACTIVE}（激活）</li>
 *   <li>{@link #ACTIVE} → {@link #SUSPENDED}（暂停）</li>
 *   <li>{@link #SUSPENDED} → {@link #ACTIVE}（激活恢复）</li>
 *   <li>{@link #ACTIVE}/{@link #SUSPENDED} → {@link #ARCHIVED}（归档）</li>
 *   <li>{@link #ARCHIVED} → {@link #SUSPENDED}（恢复归档）</li>
 * </ul>
 */
public enum WorkspaceStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    ARCHIVED
}
