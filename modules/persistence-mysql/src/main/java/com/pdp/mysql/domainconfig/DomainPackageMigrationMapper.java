package com.pdp.mysql.domainconfig;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomainPackageMigrationMapper {
  DomainPackageMigrationRow findPreview(@Param("id") UUID id);

  DomainPackageMigrationRow findJob(@Param("id") UUID id);

  int insert(DomainPackageMigrationRow row);

  int updatePreview(DomainPackageMigrationRow row);

  int updateJob(DomainPackageMigrationRow row);
}
