package com.pdp.mysql.workspace;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataScopeMapper {
  DataScopeRow findById(UUID id);

  DataScopeRow findByStableKey(
      @Param("workspaceId") UUID workspaceId, @Param("stableKey") String stableKey);

  int insert(DataScopeRow row);

  int update(DataScopeRow row);
}
