package com.pdp.mysql.project;
import java.util.*; import org.apache.ibatis.annotations.*;
@Mapper public interface ProjectStageMapper { ProjectStageRow findById(@Param("workspaceId") UUID workspaceId,@Param("projectId") UUID projectId,@Param("id") UUID id); List<ProjectStageRow> findByProjectId(@Param("workspaceId") UUID workspaceId,@Param("projectId") UUID projectId); int insert(ProjectStageRow row); int update(ProjectStageRow row); }
