package com.pdp.integration.event.port;

import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.domain.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 事件发布仓储端口。
 *
 * <p>由 {@code public-persistence} 基础设施适配器实现（{@code EventPublicationRepositoryImpl}），
 * 领域/应用层通过此端口操作 Outbox，不依赖 MyBatis 或 MySQL。
 *
 * <p>核心语义：
 * <ul>
 *   <li>{@link #save}：在业务事务内写入 Outbox（事务一致性保证事件与业务状态原子提交）</li>
 *   <li>{@link #findDispatchable}：中继调度器拉取可投递事件（PUBLISHED 或 RETRY_PENDING 且到 nextRetryAt）</li>
 *   <li>{@link #markCompleted}：监听器确认成功，更新状态为 COMPLETED</li>
 *   <li>{@link #markRetryPending}：临时失败，递增 attempts、记录 lastError、计算 nextRetryAt（指数退避）</li>
 *   <li>{@link #markDeadLettered}：重试耗尽，进入死信；保留 lastError 供人工排查</li>
 *   <li>{@link #requeueFromDeadLetter}：人工介入后将死信重新入队</li>
 * </ul>
 *
 * <p>幂等保证：{@code uniq_event_listener(event_id, listener_id)} 唯一约束防止重复投递；
 * 监听器消费时基于此约束做幂等检查。
 */
public interface EventPublicationRepository {

    /** 在业务事务内保存事件发布记录。 */
    void save(EventPublication publication);

    Optional<EventPublication> findById(UUID publicationId);

    /**
     * 拉取可投递事件（PUBLISHED 或 RETRY_PENDING 且 nextRetryAt 已到）。
     * 按 publication_date ASC 排序，限制 limit 条；返回的事件在中继分派期间不被其他调度器拾取
     * （由 SELECT ... FOR UPDATE SKIP LOCKED 或单独的 nextRetryAt 推进实现）。
     */
    List<EventPublication> findDispatchable(Instant now, int limit);

    /**
     * 标记完成（监听器确认）。
     *
     * @return true=成功；false=版本冲突或已终态
     */
    boolean markCompleted(UUID publicationId, Instant completedAt, int expectedRevision);

    /**
     * 标记重试等待（临时失败）。
     * 递增 completionAttempts、设置 lastError、lastResubmissionDate、nextRetryAt（指数退避）。
     *
     * @param expectedRevision 期望的旧 revision（乐观锁）
     * @return true=成功；false=版本冲突或已终态
     */
    boolean markRetryPending(UUID publicationId, String lastError, Instant now,
                              Instant nextRetryAt, int expectedRevision);

    /**
     * 标记死信（重试耗尽）。
     *
     * @return true=成功；false=版本冲突或已终态
     */
    boolean markDeadLettered(UUID publicationId, String finalError, Instant now, int expectedRevision);

    /**
     * 人工重新入队（死信 → RETRY_PENDING，重置 attempts）。
     *
     * @return true=成功；false=版本冲突或非死信状态
     */
    boolean requeueFromDeadLetter(UUID publicationId, String operator, Instant now, int expectedRevision);

    /** 按状态统计（运维监控用）。 */
    long countByStatus(PublicationStatus status);

    /** 查询死信事件（运维 dashboard 用）。 */
    List<EventPublication> findDeadLettered(int limit);
}
