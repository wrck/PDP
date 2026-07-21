package com.pdp.persistence.domainconfig.mapper;

import com.pdp.domainconfig.domain.packageversion.MigrationPlanStatus;
import com.pdp.persistence.domainconfig.adapter.MigrationPlanRow;
import com.pdp.domainconfig.port.MigrationPlanQueryFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 领域包迁移计划 MyBatis Mapper（FR-168 高风险操作框架）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageMigrationPlanMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageMigrationPlanMapper {

    MigrationPlanRow selectById(@Param("id") UUID id);

    List<MigrationPlanRow> selectByFilter(@Param("filter") MigrationPlanQueryFilter filter,
                                           @Param("lastId") UUID lastId,
                                           @Param("size") int size);

    MigrationPlanRow selectActiveByPackage(@Param("packageId") UUID packageId);

    MigrationPlanRow selectActiveByVersionPair(@Param("fromVersionId") UUID fromVersionId,
                                                @Param("toVersionId") UUID toVersionId);

    int insert(MigrationPlanRow row);

    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") MigrationPlanStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);

    int updateProgress(@Param("id") UUID id,
                       @Param("completedBatches") int completedBatches,
                       @Param("failedInstances") int failedInstances,
                       @Param("expectedRevision") int expectedRevision,
                       @Param("now") Instant now);

    int markReady(@Param("id") UUID id,
                  @Param("impactPreviewId") UUID impactPreviewId,
                  @Param("jobId") UUID jobId,
                  @Param("expectedRevision") int expectedRevision,
                  @Param("now") Instant now);

    int updateRollbackWindow(@Param("id") UUID id,
                              @Param("rollbackWindowExpiresAt") Instant rollbackWindowExpiresAt,
                              @Param("expectedRevision") int expectedRevision,
                              @Param("now") Instant now);
}
