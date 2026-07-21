package com.pdp.persistence.domainconfig.mapper;

import com.pdp.domainconfig.domain.packageversion.ValidationResultStatus;
import com.pdp.persistence.domainconfig.adapter.ValidationResultRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包版本校验结果 MyBatis Mapper（FR-167、SC-013）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageValidationResultMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageValidationResultMapper {

    ValidationResultRow selectById(@Param("id") UUID id);

    ValidationResultRow selectByVersionAndJob(@Param("versionId") UUID versionId,
                                               @Param("jobId") UUID jobId);

    ValidationResultRow selectLatestByVersion(@Param("versionId") UUID versionId);

    int insert(ValidationResultRow row);

    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") ValidationResultStatus newStatus,
                     @Param("passed") boolean passed,
                     @Param("now") Instant now);
}
