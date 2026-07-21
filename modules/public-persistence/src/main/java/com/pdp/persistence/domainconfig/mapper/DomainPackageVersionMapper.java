package com.pdp.persistence.domainconfig.mapper;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;
import com.pdp.domainconfig.port.DomainPackageVersionQueryFilter;
import com.pdp.persistence.domainconfig.adapter.DomainPackageVersionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 领域包版本 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/domainconfig/DomainPackageVersionMapper.xml} 中声明。
 *
 * <p>承载版本草稿、校验、审核、发布、冻结、回滚、弃用、退役等完整生命周期状态迁移。
 * 终态迁移（submit/reject/publish/freeze/deprecate/retire/rollback）通过专用方法同时更新审计字段。
 */
@Mapper
public interface DomainPackageVersionMapper {

    DomainPackageVersionRow selectById(UUID id);

    DomainPackageVersionRow selectByPackageAndSemanticVersion(@Param("packageId") UUID packageId,
                                                                @Param("semanticVersion") String semanticVersion);

    List<DomainPackageVersionRow> selectByFilter(@Param("filter") DomainPackageVersionQueryFilter filter,
                                                   @Param("lastId") UUID lastId,
                                                   @Param("size") int size);

    /** 查找领域包当前已发布版本（status=PUBLISHED 或 FROZEN），按 createdAt 降序取首条。 */
    DomainPackageVersionRow selectCurrentPublished(UUID packageId);

    /** 查找领域包最新草稿（status=DRAFT 或 REJECTED），按 createdAt 降序取首条。 */
    DomainPackageVersionRow selectLatestDraft(UUID packageId);

    int insert(DomainPackageVersionRow row);

    int updateDraft(@Param("id") UUID id,
                    @Param("manifestJson") String manifestJson,
                    @Param("contentHash") String contentHash,
                    @Param("compatibilityStatementJson") String compatibilityStatementJson,
                    @Param("extendsVersionRange") String extendsVersionRange,
                    @Param("expectedRevision") int expectedRevision,
                    @Param("now") Instant now);

    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") DomainPackageVersionStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);

    int submitForReview(@Param("id") UUID id,
                        @Param("submittedBy") String submittedBy,
                        @Param("submittedAt") Instant submittedAt,
                        @Param("expectedRevision") int expectedRevision);

    int reject(@Param("id") UUID id,
               @Param("rejectedBy") String rejectedBy,
               @Param("rejectedAt") Instant rejectedAt,
               @Param("rejectReason") String rejectReason,
               @Param("expectedRevision") int expectedRevision);

    int publish(@Param("id") UUID id,
                @Param("publishedBy") String publishedBy,
                @Param("publishedAt") Instant publishedAt,
                @Param("runtimeSnapshotId") String runtimeSnapshotId,
                @Param("parentSnapshotId") String parentSnapshotId,
                @Param("expectedRevision") int expectedRevision);

    int freeze(@Param("id") UUID id,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);

    int deprecate(@Param("id") UUID id,
                  @Param("expectedRevision") int expectedRevision,
                  @Param("now") Instant now);

    int retire(@Param("id") UUID id,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);

    int rollback(@Param("id") UUID id,
                 @Param("rolledBackBy") String rolledBackBy,
                 @Param("rolledBackAt") Instant rolledBackAt,
                 @Param("rollbackReason") String rollbackReason,
                 @Param("expectedRevision") int expectedRevision);
}
