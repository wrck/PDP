package com.pdp.mysql.domainconfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomainPackageVersionMapper {
  DomainPackageVersionRow findById(UUID id);

  DomainPackageVersionRow findByPackageAndSemanticVersion(
      @Param("packageId") UUID packageId, @Param("semanticVersion") String semanticVersion);

  List<DomainPackageVersionRow> findByPackageId(UUID packageId);

  List<DomainPackageVersionRow> findPageAfter(
      @Param("packageId") UUID packageId,
      @Param("afterCreatedAt") Instant afterCreatedAt,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);

  int insert(DomainPackageVersionRow row);

  int update(DomainPackageVersionRow row);
}
