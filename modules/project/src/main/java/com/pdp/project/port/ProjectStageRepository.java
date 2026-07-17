package com.pdp.project.port;

import com.pdp.project.domain.ProjectStage;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 阶段端口提供按项目稳定排序的生命周期阶段读写。 */
public interface ProjectStageRepository {
  Optional<ProjectStage> findById(UUID workspaceId, UUID projectId, UUID stageId);
  List<ProjectStage> findByProjectId(UUID workspaceId, UUID projectId);
  ProjectStage insert(ProjectStage stage);
  ProjectStage update(ProjectStage stage, Revision expectedRevision);
}
