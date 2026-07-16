package com.pdp.mysql.domainconfig;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainPackageSnapshotMapper {
  int insert(DomainPackageSnapshotRow row);
}
