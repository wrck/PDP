package com.pdp.mysql.workspace;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkspaceMembershipMapper {
  WorkspaceMembershipRow findById(UUID id);

  WorkspaceMembershipRow findByWorkspaceAndUser(
      @Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

  List<WorkspaceMembershipRow> findByWorkspaceId(UUID workspaceId);

  int insert(WorkspaceMembershipRow row);

  int update(WorkspaceMembershipRow row);
}
