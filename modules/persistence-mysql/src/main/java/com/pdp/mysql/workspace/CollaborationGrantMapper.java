package com.pdp.mysql.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollaborationGrantMapper {
  CollaborationGrantRow findById(UUID id);

  List<CollaborationGrantRow> findByOwnerWorkspaceId(UUID workspaceId);

  List<CollaborationGrantRow> findActive(
      @Param("ownerWorkspaceId") UUID ownerWorkspaceId,
      @Param("collaboratorWorkspaceId") UUID collaboratorWorkspaceId,
      @Param("at") Instant at);

  int insert(CollaborationGrantRow row);

  int update(CollaborationGrantRow row);
}
