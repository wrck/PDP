package com.pdp.persistence.integration.adapter;

import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.domain.PublicationStatus;
import com.pdp.integration.event.port.EventPublicationRepository;
import com.pdp.persistence.integration.mapper.EventPublicationMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 事件发布仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link EventPublicationRepository} 端口，委托 {@link EventPublicationMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.integration.adapter}，被领域/应用层通过端口消费。
 *
 * <p>关键语义：
 * <ul>
 *   <li>{@link #findDispatchable} 在事务内调用 {@code FOR UPDATE SKIP LOCKED} 保持行锁，
 *       防止多副本中继调度器重复拾取同一事件</li>
 *   <li>所有状态迁移使用 revision 乐观锁（{@code WHERE revision = #{expectedRevision}}），
 *       返回 {@code false} 表示版本冲突或已终态</li>
 *   <li>{@link #save} 在调用方事务内执行（与业务状态变更原子提交）</li>
 * </ul>
 */
@Repository
public class EventPublicationRepositoryImpl implements EventPublicationRepository {

    private final EventPublicationMapper mapper;

    public EventPublicationRepositoryImpl(EventPublicationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(EventPublication publication) {
        int rows = mapper.insert(publication);
        if (rows != 1) {
            throw new IllegalStateException("事件发布记录插入失败: " + publication.publicationId());
        }
    }

    @Override
    public Optional<EventPublication> findById(UUID publicationId) {
        return Optional.ofNullable(mapper.selectById(publicationId));
    }

    /**
     * 拉取可投递事件。事务边界由 {@code EventRelayService.dispatchBatch} 控制（{@code @Transactional}），
     * 此处仅保证 {@code FOR UPDATE SKIP LOCKED} 在事务内执行。
     */
    @Override
    @Transactional
    public List<EventPublication> findDispatchable(Instant now, int limit) {
        return mapper.selectDispatchableForUpdateSkipLocked(now, limit);
    }

    @Override
    public boolean markCompleted(UUID publicationId, Instant completedAt, int expectedRevision) {
        return mapper.updateMarkCompleted(publicationId, completedAt, expectedRevision) == 1;
    }

    @Override
    public boolean markRetryPending(UUID publicationId, String lastError, Instant now,
                                     Instant nextRetryAt, int expectedRevision) {
        return mapper.updateMarkRetryPending(publicationId, lastError, now, nextRetryAt, expectedRevision) == 1;
    }

    @Override
    public boolean markDeadLettered(UUID publicationId, String finalError, Instant now, int expectedRevision) {
        return mapper.updateMarkDeadLettered(publicationId, finalError, now, expectedRevision) == 1;
    }

    @Override
    public boolean requeueFromDeadLetter(UUID publicationId, String operator, Instant now, int expectedRevision) {
        return mapper.updateRequeueFromDeadLetter(publicationId, operator, now, expectedRevision) == 1;
    }

    @Override
    public long countByStatus(PublicationStatus status) {
        return mapper.countByStatus(status);
    }

    @Override
    public List<EventPublication> findDeadLettered(int limit) {
        return mapper.selectDeadLettered(limit);
    }
}
