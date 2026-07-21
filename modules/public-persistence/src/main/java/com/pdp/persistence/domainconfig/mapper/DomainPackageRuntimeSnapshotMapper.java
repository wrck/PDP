package com.pdp.persistence.domainconfig.mapper;

import com.pdp.persistence.domainconfig.adapter.RuntimeSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * 领域包运行时版本快照 MyBatis Mapper（FR-018）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageRuntimeSnapshotMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageRuntimeSnapshotMapper {

    RuntimeSnapshotRow selectById(@Param("snapshotId") String snapshotId);

    RuntimeSnapshotRow selectByVersionId(@Param("versionId") UUID versionId);

    RuntimeSnapshotRow selectLatestByPackageId(@Param("packageId") UUID packageId);

    int insert(RuntimeSnapshotRow row);

    String selectResolvedObjectsJson(@Param("snapshotId") String snapshotId);
}
