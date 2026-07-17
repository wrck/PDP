package com.pdp.integration.event.application;

import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.domain.PublicationStatus;
import com.pdp.integration.event.port.EventListener;
import com.pdp.integration.event.port.EventPublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 事件中继服务。
 *
 * <p>从 Outbox 拾取可投递事件，按 {@code eventType} 路由到注册的 {@link EventListener}。
 * 单次拾取批次大小、重试退避、最大重试次数由配置控制；超限事件进入死信。
 *
 * <p>关键设计：
 * <ul>
 *   <li>每次拾取一批（默认 50 条），逐条投递；投递失败记录 lastError、推进 nextRetryAt</li>
 *   <li>重试退避：{@code base * 2^attempts}，封顶 5 分钟；超过 {@code MAX_ATTEMPTS=5} 进入死信</li>
 *   <li>分派事务：每个事件投递在独立事务（{@code REQUIRES_NEW}）中执行，
 *       监听器失败仅回滚当前事件，不影响其他事件</li>
 *   <li>幂等：监听器内部基于 {@code eventId + listenerId} 做幂等检查；
 *       重复投递由监听器自行处理（视为 no-op）</li>
 *   <li>无 XA：单库本地事务，禁止事务内切换数据源</li>
 * </ul>
 *
 * <p>对应规格：FR-029~FR-038（事件投递语义）、FR-160（撤销时效 — 事件中继 SLA）。
 */
@Service
public class EventRelayService {

    private static final Logger log = LoggerFactory.getLogger(EventRelayService.class);

    /** 单次拾取批次大小（运维可调）。 */
    public static final int DEFAULT_BATCH_SIZE = 50;

    /** 最大重试次数（attempts >= MAX 时进入死信）。 */
    public static final int MAX_ATTEMPTS = 5;

    /** 退避基数（首次重试延迟）。 */
    public static final Duration BASE_BACKOFF = Duration.ofSeconds(5);

    /** 退避封顶（最长重试间隔）。 */
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final EventPublicationRepository repository;
    /** 按 eventType 分组的监听器映射。 */
    private final Map<String, List<EventListener>> listenersByType;

    @Autowired
    public EventRelayService(EventPublicationRepository repository,
                              List<EventListener> listeners) {
        this.repository = repository;
        this.listenersByType = listeners.stream()
                .collect(Collectors.groupingBy(EventListener::eventType));
        log.info("EventRelayService 初始化: 注册 {} 个监听器，覆盖 {} 个事件类型",
                listeners.size(), this.listenersByType.size());
    }

    /**
     * 拾取并分派一批事件。由 {@code EventRelayScheduler} 定时调用。
     *
     * @return 本批次处理的事件数（成功 + 失败）
     */
    public int dispatchBatch(int batchSize) {
        Instant now = Instant.now();
        List<EventPublication> pending = repository.findDispatchable(now, batchSize);
        if (pending.isEmpty()) {
            return 0;
        }
        log.debug("EventRelay 拾取 {} 条事件", pending.size());
        int processed = 0;
        for (EventPublication publication : pending) {
            dispatchOne(publication, now);
            processed++;
        }
        return processed;
    }

    /**
     * 分派单个事件。
     *
     * <p>使用 {@code REQUIRES_NEW} 在独立事务中执行：
     * 监听器异常仅回滚当前事件的状态更新，不影响其他事件。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(EventPublication publication, Instant now) {
        List<EventListener> listeners = listenersByType.getOrDefault(publication.eventType(), List.of());
        if (listeners.isEmpty()) {
            // 无监听器：直接标记完成（避免事件无限堆积）
            log.debug("EventRelay 事件 {} 无监听器，标记完成", publication.eventId());
            repository.markCompleted(publication.publicationId(), now, publication.revision());
            return;
        }
        // 逐个监听器投递；任一失败即视为整体失败
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(publication);
            } catch (Exception e) {
                handleFailure(publication, listener, e, now);
                return;
            }
        }
        // 全部监听器确认：标记完成
        repository.markCompleted(publication.publicationId(), now, publication.revision());
    }

    /** 处理投递失败：递增 attempts，决定重试或死信。 */
    private void handleFailure(EventPublication publication, EventListener listener,
                                Exception error, Instant now) {
        String errorMsg = String.format("[%s] %s: %s",
                listener.listenerId(), error.getClass().getSimpleName(), error.getMessage());
        int nextAttempt = publication.completionAttempts() + 1;

        if (nextAttempt >= MAX_ATTEMPTS) {
            log.warn("EventRelay 事件 {} 重试耗尽（attempts={}），进入死信: {}",
                    publication.eventId(), nextAttempt, errorMsg);
            repository.markDeadLettered(publication.publicationId(), errorMsg, now, publication.revision());
        } else {
            Instant nextRetry = now.plus(computeBackoff(nextAttempt));
            log.info("EventRelay 事件 {} 投递失败（attempts={}），将在 {} 后重试: {}",
                    publication.eventId(), nextAttempt, nextRetry, errorMsg);
            repository.markRetryPending(publication.publicationId(), errorMsg, now, nextRetry,
                    publication.revision());
        }
    }

    /** 指数退避：{@code base * 2^(attempts-1)}，封顶 {@code MAX_BACKOFF}。 */
    private Duration computeBackoff(int nextAttempt) {
        long multiplier = 1L << (nextAttempt - 1); // 2^(attempts-1)
        Duration backoff = BASE_BACKOFF.multipliedBy(multiplier);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    /** 运维接口：查询死信事件供人工介入。 */
    public List<EventPublication> listDeadLettered(int limit) {
        return repository.findDeadLettered(limit);
    }

    /** 运维接口：人工重新入队死信事件。 */
    public boolean requeue(UUID publicationId, String operator) {
        // 查询当前事件以获取 revision
        return repository.findById(publicationId).map(p -> {
            if (p.status() != PublicationStatus.DEAD_LETTERED) {
                return false;
            }
            return repository.requeueFromDeadLetter(publicationId, operator, Instant.now(), p.revision());
        }).orElse(false);
    }
}
