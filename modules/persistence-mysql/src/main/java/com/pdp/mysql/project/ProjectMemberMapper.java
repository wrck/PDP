package com.pdp.mysql.project;
import java.time.Instant; import java.util.*; import org.apache.ibatis.annotations.*;
@Mapper public interface ProjectMemberMapper { List<ProjectMemberRow> findActiveByProjectId(@Param("workspaceId") UUID workspaceId,@Param("projectId") UUID projectId,@Param("at") Instant at); int insert(ProjectMemberRow row); int update(ProjectMemberRow row); }
