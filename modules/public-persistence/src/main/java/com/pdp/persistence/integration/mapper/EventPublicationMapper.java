package com.pdp.persistence.integration.mapper;

import com.pdp.integration.event.domain.EventPublication;
import com.pdp.integration.event.domain.PublicationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 事件发布 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口；所有 SQL 在 {@code resources/mapper/integration/EventPublicationMapper.xml} 中声明。
 *
 * <p>关键查询：{@link #selectDispatchableForUpdateSkipLocked} 使用 MySQL 8.0+
 * {@code FOR UPDATE SKIP LOCKED} 防止多副本中继调度器重复拾取同一事件；
 * 单条 UPDATE 通过 revision 乐观锁保证状态迁移原子性。
 */
@Mapper
public interface EventPublicationMapper {

    int insert(EventPublication publication);

    EventPublication selectById(UUID publicationId);

    /**
     * 拉取可投递事件（PUBLISHED 或 RETRY_PENDING 且 nextRetryAt 已到）。
     *
     * <p>使用 {@code FOR UPDATE SKIP LOCKED} 在 MySQL 8.0+ 上避免多副本重复拾取；
     * 调用方需在事务中调用以保持行锁。
     */
    List<EventPublication> selectDispatchableForUpdateSkipLocked(
            @Param("now") Instant now, @Param("limit") int limit);

    int updateMarkCompleted(@Param("id") UUID publicationId,
                            @Param("completedAt") Instant completedAt,
                            @Param("expectedRevision") int expectedRevision);

    int updateMarkRetryPending(@Param("id") UUID publicationId,
                               @Param("lastError") String lastError,
                               @Param("now") Instant now,
                               @Param("nextRetryAt") Instant nextRetryAt,
                               @Param("expectedRevision") int expectedRevision);

    int updateMarkDeadLettered(@Param("id") UUID publicationId,
                               @Param("finalError") String finalError,
                               @Param("now") Instant now,
                               @Param("expectedRevision") int expectedRevision);

    int updateRequeueFromDeadLetter(@Param("id") UUID publicationId,
                                    @Param("operator") String operator,
                                    @Param("now") Instant now,
                                    @Param("expectedRevision") int expectedRevision);

    long countByStatus(@Param("status") PublicationStatus status);

    List<EventPublication> selectDeadLettered(@Param("limit") int limit);
}
