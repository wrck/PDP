package com.pdp.mysql.workspace;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkspaceRoleMapper {
  WorkspaceRoleRow findById(UUID id);

  WorkspaceRoleRow findByStableKey(
      @Param("workspaceId") UUID workspaceId, @Param("stableKey") String stableKey);

  List<WorkspaceRoleRow> findByWorkspaceId(UUID workspaceId);

  int insert(WorkspaceRoleRow row);

  int update(WorkspaceRoleRow row);
}
