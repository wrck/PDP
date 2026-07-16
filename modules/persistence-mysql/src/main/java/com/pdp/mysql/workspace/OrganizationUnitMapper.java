package com.pdp.mysql.workspace;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationUnitMapper {
  OrganizationUnitRow findById(UUID id);

  OrganizationUnitRow findByWorkspaceAndCode(
      @Param("workspaceId") UUID workspaceId, @Param("code") String code);

  List<OrganizationUnitRow> findByWorkspaceId(UUID workspaceId);

  List<OrganizationUnitRow> findPageAfter(
      @Param("workspaceId") UUID workspaceId,
      @Param("afterPath") String afterPath,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);

  int insert(OrganizationUnitRow row);

  int update(OrganizationUnitRow row);
}
