package com.pdp.mysql.domainconfig;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomainPackageMapper {
  DomainPackageRow findById(UUID id);

  DomainPackageRow findByStableKey(
      @Param("workspaceId") UUID workspaceId, @Param("stableKey") String stableKey);

  List<DomainPackageRow> findByWorkspace(UUID workspaceId);

  List<DomainPackageRow> findPageAfter(
      @Param("workspaceId") UUID workspaceId,
      @Param("afterStableKey") String afterStableKey,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);

  int insert(DomainPackageRow row);

  int update(DomainPackageRow row);
}
