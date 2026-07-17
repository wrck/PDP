package com.pdp.integration.event.domain;

/**
 * 事件发布状态机。
 *
 * <p>状态迁移：
 * <pre>
 *   PUBLISHED ──relay──► COMPLETED         （监听器确认）
 *   PUBLISHED ──relay──► RETRY_PENDING     （临时失败，等待重试）
 *   RETRY_PENDING ──relay──► COMPLETED     （重试成功）
 *   RETRY_PENDING ──relay──► RETRY_PENDING （再次失败，attempts++，next_retry_at 递增）
 *   RETRY_PENDING ──exhausted──► DEAD_LETTERED  （attempts >= MAX_ATTEMPTS）
 *   DEAD_LETTERED ──manual──► RETRY_PENDING     （人工介入后重新入队）
 * </pre>
 *
 * <p>对应表 event_publication.status；持久化使用 name() 稳定键。
 */
public enum PublicationStatus {

    /** 已写入 Outbox，等待中继投递。 */
    PUBLISHED,

    /** 已成功投递并被全部监听器确认。 */
    COMPLETED,

    /** 投递失败，等待重试（exponential backoff）。 */
    RETRY_PENDING,

    /** 重试耗尽，进入死信；需人工介入或补偿。 */
    DEAD_LETTERED;

    /** 是否允许重试（仅 RETRY_PENDING 可被中继再次拾取）。 */
    public boolean isRetryable() {
        return this == RETRY_PENDING;
    }

    /** 是否终态（COMPLETED / DEAD_LETTERED）。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == DEAD_LETTERED;
    }
}
