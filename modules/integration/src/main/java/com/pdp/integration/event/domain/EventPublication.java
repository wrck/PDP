package com.pdp.integration.event.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件发布记录（Outbox 行）。
 *
 * <p>对应表 {@code event_publication}；领域/应用层通过
 * {@link com.pdp.integration.event.port.EventPublicationRepository} 端口操作，
 * 不感知 MyBatis 或 MySQL 类型。
 *
 * <p>字段对应：
 * <ul>
 *   <li>{@code publicationId} ↔ publication_id（PK，UUIDv7）</li>
 *   <li>{@code eventId} ↔ event_id（业务事件 ID，与 listener_id 联合唯一）</li>
 *   <li>{@code eventType} ↔ event_type（稳定事件类型）</li>
 *   <li>{@code version} ↔ event_version（schema 版本）</li>
 *   <li>{@code listenerId} ↔ listener_id（监听器标识，null 表示尚未分派）</li>
 *   <li>{@code aggregateType} ↔ aggregate_type</li>
 *   <li>{@code aggregateId} ↔ aggregate_id</li>
 *   <li>{@code payload} ↔ payload（JSON）</li>
 *   <li>{@code status} ↔ status</li>
 *   <li>{@code publicationDate} ↔ publication_date</li>
 *   <li>{@code completionAttempts} ↔ completion_attempts</li>
 *   <li>{@code lastResubmissionDate} ↔ last_resubmission_date</li>
 *   <li>{@code completionDate} ↔ completion_date</li>
 *   <li>{@code lastError} ↔ last_error</li>
 *   <li>{@code nextRetryAt} ↔ next_retry_at</li>
 * </ul>
 */
public record EventPublication(
        UUID publicationId,
        UUID eventId,
        String eventType,
        String version,
        String listenerId,
        String aggregateType,
        UUID aggregateId,
        JsonNode payload,
        PublicationStatus status,
        Instant publicationDate,
        int completionAttempts,
        Instant lastResubmissionDate,
        Instant completionDate,
        String lastError,
        Instant nextRetryAt,
        int revision) {

    public EventPublication {
        if (publicationId == null) {
            throw new IllegalArgumentException("publicationId 不能为 null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId 不能为 null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 不能为空");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        if (publicationDate == null) {
            throw new IllegalArgumentException("publicationDate 不能为 null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload 不能为 null");
        }
    }

    /** 是否可被中继再次投递（PUBLISHED 或 RETRY_PENDING 且 nextRetryAt 已到）。 */
    public boolean isDispatchable(Instant now) {
        return (status == PublicationStatus.PUBLISHED || status.isRetryable())
                && (nextRetryAt == null || !now.isBefore(nextRetryAt));
    }
}
