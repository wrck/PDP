package com.pdp.persistence.domainconfig.mapper;

import com.pdp.persistence.domainconfig.adapter.ImpactPreviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包升级影响预览 MyBatis Mapper（FR-168 高风险操作框架）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageImpactPreviewMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageImpactPreviewMapper {

    ImpactPreviewRow selectById(@Param("id") UUID id);

    ImpactPreviewRow selectLatestByCandidateVersion(@Param("candidateVersionId") UUID candidateVersionId);

    ImpactPreviewRow selectLatestByPackageAndVersionPair(@Param("packageId") UUID packageId,
                                                          @Param("currentVersionId") UUID currentVersionId,
                                                          @Param("candidateVersionId") UUID candidateVersionId);

    int insert(ImpactPreviewRow row);

    int confirm(@Param("id") UUID id,
                @Param("confirmedBy") String confirmedBy,
                @Param("confirmedAt") Instant confirmedAt,
                @Param("now") Instant now);

    int deleteExpired(@Param("now") Instant now);
}
