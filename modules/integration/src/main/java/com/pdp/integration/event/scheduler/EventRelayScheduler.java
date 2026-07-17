package com.pdp.integration.event.scheduler;

import com.pdp.integration.event.application.EventRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件中继调度器。
 *
 * <p>定时触发 {@link EventRelayService#dispatchBatch} 拾取并分派 Outbox 事件。
 * 默认每 5 秒一次，单实例运行（多副本环境通过 ShedLock 或数据库行锁防止重复拾取，
 * 由 {@code EventPublicationRepository.findDispatchable} 的 FOR UPDATE SKIP LOCKED 兜底）。
 *
 * <p>调度参数可通过 {@code application.yml} 的 {@code pdp.event.relay.*} 覆盖。
 *
 * <p>对应规格：FR-029~FR-038（事件投递时效）、SC-024（事件中继 SLA）。
 */
@Component
public class EventRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventRelayScheduler.class);

    private final EventRelayService relayService;

    public EventRelayScheduler(EventRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * 每 5 秒触发一次事件中继。
     *
     * <p>fixedDelay=5000ms：上一次执行结束后等待 5 秒再触发，避免堆叠；
     * initialDelay=5000ms：应用启动 5 秒后开始，避开启动峰值。
     */
    @Scheduled(fixedDelayString = "${pdp.event.relay.fixed-delay-ms:5000}",
               initialDelayString = "${pdp.event.relay.initial-delay-ms:5000}")
    public void relayEvents() {
        try {
            int processed = relayService.dispatchBatch(EventRelayService.DEFAULT_BATCH_SIZE);
            if (processed > 0) {
                log.debug("EventRelay 调度完成: 处理 {} 条事件", processed);
            }
        } catch (Exception e) {
            // 调度异常不传播，避免 Spring 调度器停用
            log.error("EventRelay 调度异常", e);
        }
    }
}
