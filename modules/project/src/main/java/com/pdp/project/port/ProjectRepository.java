package com.pdp.project.port;

import com.pdp.project.domain.Project;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 项目仓储端口；每项读取均以工作空间作为强制边界。 */
public interface ProjectRepository {
  Optional<Project> findById(UUID workspaceId, UUID projectId);
  Optional<Project> findByProjectNo(UUID workspaceId, String projectNo);
  List<Project> findChildren(UUID workspaceId, UUID parentProjectId);
  Project insert(Project project);
  Project update(Project project, Revision expectedRevision);
}
