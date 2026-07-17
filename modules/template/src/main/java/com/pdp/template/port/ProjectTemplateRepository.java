package com.pdp.template.port;

import com.pdp.shared.concurrency.Revision;
import com.pdp.template.domain.ProjectTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 项目模板根聚合仓储；所有读写必须显式限定工作空间。 */
public interface ProjectTemplateRepository {
  Optional<ProjectTemplate> findById(UUID workspaceId, UUID templateId);

  Optional<ProjectTemplate> findByStableKey(UUID workspaceId, String stableKey);

  List<ProjectTemplate> findByWorkspace(UUID workspaceId, ProjectTemplate.Status status);

  ProjectTemplate insert(ProjectTemplate template);

  ProjectTemplate update(ProjectTemplate template, Revision expectedRevision);
}
