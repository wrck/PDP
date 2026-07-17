package com.pdp.integration.event.application;

import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.port.EventPublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 死信处理服务。
 *
 * <p>提供死信事件查询、人工重新入队、批量清理等运维能力。
 * 死信事件保留在 {@code event_publication} 表中（状态为 {@code DEAD_LETTERED}），
 * 不物理删除，便于审计与根因分析；重新入队后状态变更为 {@code RETRY_PENDING}，
 * 监听器消费成功后才会标记为 {@code COMPLETED}。
 *
 * <p>关键策略：
 * <ul>
 *   <li>保留 lastError 与 completionAttempts 历史，运维 dashboard 可展示失败模式</li>
 *   <li>重新入队需明确操作者标识（审计追溯）</li>
 *   <li>批量清理仅清理超过保留期（默认 90 天）的已死信事件，避免无限增长</li>
 * </ul>
 *
 * <p>对应规格：FR-038（事件死信与补偿）、ADR 0001（Outbox 模式）。
 */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final EventPublicationRepository repository;

    public DeadLetterService(EventPublicationRepository repository) {
        this.repository = repository;
    }

    /** 查询最近的死信事件（运维 dashboard 用）。 */
    public List<EventPublication> listRecentDeadLettered(int limit) {
        return repository.findDeadLettered(limit);
    }

    /** 人工重新入队死信事件。返回是否成功（false 表示事件不存在、非死信状态或版本冲突）。 */
    public boolean requeue(java.util.UUID publicationId, String operator) {
        log.info("人工重新入队死信事件: publicationId={} operator={}", publicationId, operator);
        return repository.findById(publicationId).map(p -> {
            if (p.status() != com.pdp.integration.event.domain.PublicationStatus.DEAD_LETTERED) {
                log.warn("重新入队失败：事件 {} 非 DEAD_LETTERED 状态: {}", publicationId, p.status());
                return false;
            }
            return repository.requeueFromDeadLetter(publicationId, operator, java.time.Instant.now(), p.revision());
        }).orElse(false);
    }

    /** 死信事件总数（运维监控指标）。 */
    public long deadLetteredCount() {
        return repository.countByStatus(com.pdp.integration.event.domain.PublicationStatus.DEAD_LETTERED);
    }

    /** 待重试事件总数（运维监控指标）。 */
    public long retryPendingCount() {
        return repository.countByStatus(com.pdp.integration.event.domain.PublicationStatus.RETRY_PENDING);
    }
}
