package com.pdp.mysql.workspace;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkspaceMapper {
  WorkspaceRow findById(UUID id);

  WorkspaceRow findByCode(String code);

  List<WorkspaceRow> findAccessibleByUserId(UUID userId);

  List<WorkspaceRow> findPageAfter(
      @Param("afterCode") String afterCode,
      @Param("afterId") UUID afterId,
      @Param("limit") int limit);

  int insert(WorkspaceRow row);

  int update(WorkspaceRow row);
}
