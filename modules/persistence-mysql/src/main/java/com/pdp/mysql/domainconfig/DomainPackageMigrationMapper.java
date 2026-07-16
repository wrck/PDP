package com.pdp.mysql.domainconfig;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainPackageMigrationMapper {
  DomainPackageMigrationRow findPreview(UUID id);

  DomainPackageMigrationRow findJob(UUID id);

  int insert(DomainPackageMigrationRow row);

  int updatePreview(DomainPackageMigrationRow row);

  int updateJob(DomainPackageMigrationRow row);
}
