package com.pdp.integration.event.port;

import com.pdp.integration.event.domain.EventPublication;

/**
 * 事件监听器契约。
 *
 * <p>每个监听器以稳定 {@code listenerId} 标识自身（用于幂等去重），
 * 接收 {@link EventPublication} 后处理业务副作用；任何抛出异常视为投递失败，
 * 由 {@code EventRelayService} 决定重试或进入死信。
 *
 * <p>监听器实现建议位于各业务模块的 {@code infrastructure.event} 子包，
 * 通过 Spring {@code @Component} 自动注册；{@code EventRelayService} 通过
 * Spring {@code ApplicationContext} 收集全部实现并按 {@code eventType()} 路由。
 *
 * <p>幂等保证：监听器内部必须基于 {@code eventId + listenerId} 做幂等检查，
 * 或调用 {@code IdempotencyRecordRepository}（T060 同批次）记录已处理事件。
 */
public interface EventListener {

    /** 监听器稳定标识（与 event_id 联合唯一约束，用于幂等）。 */
    String listenerId();

    /** 监听的事件类型；多个监听器可监听同一事件类型（fan-out）。 */
    String eventType();

    /**
     * 处理事件。
     *
     * @param publication 事件发布记录（含 payload、aggregate 引用）
     * @throws Exception 任何异常视为投递失败，触发重试/死信流程
     */
    void onEvent(EventPublication publication) throws Exception;
}
