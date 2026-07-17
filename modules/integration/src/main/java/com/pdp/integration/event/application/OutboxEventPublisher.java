package com.pdp.integration.event.application;

import com.pdp.integration.event.domain.DomainEvent;
import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.domain.PublicationStatus;
import com.pdp.integration.event.infrastructure.jackson.EventJsonSerializer;
import com.pdp.integration.event.port.EventPublicationRepository;
import com.pdp.shared.id.UuidV7Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Outbox 事件发布器。
 *
 * <p>业务模块在事务内调用 {@link #publish} 写入 Outbox；
 * 事务提交后，{@code EventRelayService} 异步拾取并分派给监听器。
 *
 * <p>关键保证：
 * <ul>
 *   <li>事务一致性：事件写入与业务状态变更在同一事务内，原子提交（避免双写不一致）</li>
 *   <li>无 XA：单库本地事务（{@code pdpPrimary}），由 Outbox + Relay 实现最终一致性</li>
 *   <li>事件不可变：写入后 payload、eventType、aggregate 引用不变；只允许状态字段更新</li>
 *   <li>幂等消费：监听器消费时基于 {@code eventId + listenerId} 做幂等检查</li>
 * </ul>
 *
 * <p>对应规格：FR-029~FR-038、ADR 0001（模块化单体 Outbox 模式）。
 */
@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final EventPublicationRepository repository;
    private final EventJsonSerializer serializer;

    public OutboxEventPublisher(EventPublicationRepository repository, EventJsonSerializer serializer) {
        this.repository = repository;
        this.serializer = serializer;
    }

    /**
     * 在当前事务内发布事件到 Outbox。
     *
     * <p>调用方应在 {@code @Transactional} 方法内调用此方法；
     * 事务回滚时，Outbox 写入一并回滚，不会产生孤立事件。
     *
     * @param event 领域事件（实现 {@link DomainEvent}）
     */
    @Transactional
    public void publish(DomainEvent event) {
        Instant now = Instant.now();
        EventPublication publication = new EventPublication(
                UuidV7Generator.next(),
                event.eventId(),
                event.eventType(),
                event.version(),
                null, // listenerId 在中继分派时设置
                event.aggregateType(),
                event.aggregateId(),
                serializer.serialize(event),
                PublicationStatus.PUBLISHED,
                now,
                0,
                null,
                null,
                null,
                null,
                1);
        repository.save(publication);
        log.debug("Outbox 事件发布: type={} eventId={} aggregateType={} aggregateId={}",
                event.eventType(), event.eventId(), event.aggregateType(), event.aggregateId());
    }
}
