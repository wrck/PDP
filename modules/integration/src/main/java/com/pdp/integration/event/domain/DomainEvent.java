package com.pdp.integration.event.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件契约。
 *
 * <p>所有发布到 Outbox 的事件必须实现此接口，提供稳定的事件类型标识、版本与聚合引用，
 * 供 {@code EventRelayService} 路由到正确监听器与下游消费者。
 *
 * <p>事件不可变；事件 ID 使用 UUIDv7，便于按时间排序且全局唯一。
 * payload 由具体事件类型自身字段表示，{@code EventJsonSerializer} 负责序列化为 JSON 存入 outbox。
 *
 * <p>对应规格：FR-029~FR-038（事件契约）、ADR 0001（模块化单体 Outbox 模式）。
 */
public interface DomainEvent {

    /** 事件唯一 ID（UUIDv7，时间排序友好）。 */
    UUID eventId();

    /** 稳定事件类型（{@code com.pdp.<module>.<Aggregate><Action>}，如 {@code com.pdp.identity.UserActivated}）。 */
    String eventType();

    /** 事件 schema 版本（语义化版本，如 {@code 1.0.0}）。 */
    String version();

    /** 聚合类型（{@code UserAccount}、{@code Workspace} 等）。 */
    String aggregateType();

    /** 聚合 ID（聚合根 UUIDv7）。 */
    UUID aggregateId();

    /** 事件发生时间。 */
    Instant occurredAt();
}
