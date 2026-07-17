package com.pdp.mysql.project;
import java.util.*; import org.apache.ibatis.annotations.*;
@Mapper public interface ProjectMapper { ProjectRow findById(@Param("workspaceId") UUID workspaceId,@Param("id") UUID id); ProjectRow findByProjectNo(@Param("workspaceId") UUID workspaceId,@Param("projectNo") String projectNo); List<ProjectRow> findChildren(@Param("workspaceId") UUID workspaceId,@Param("parentProjectId") UUID parentProjectId); List<UUID> findAncestors(@Param("workspaceId") UUID workspaceId,@Param("projectId") UUID projectId); int insert(ProjectRow row); int update(ProjectRow row); }
