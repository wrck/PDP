package com.pdp.template.port;

import com.pdp.shared.concurrency.Revision;
import com.pdp.template.domain.ProjectTemplateVersion;
import com.pdp.template.domain.SemanticVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 模板版本仓储；适配器必须通过模板所属关系实施工作空间隔离。 */
public interface ProjectTemplateVersionRepository {
  Optional<ProjectTemplateVersion> findById(
      UUID workspaceId, UUID templateId, UUID templateVersionId);

  Optional<ProjectTemplateVersion> findBySemanticVersion(
      UUID workspaceId, UUID templateId, SemanticVersion semanticVersion);

  List<ProjectTemplateVersion> findByTemplate(UUID workspaceId, UUID templateId);

  Optional<ProjectTemplateVersion> findCurrentPublished(UUID workspaceId, UUID templateId);

  ProjectTemplateVersion insert(UUID workspaceId, ProjectTemplateVersion version);

  ProjectTemplateVersion update(
      UUID workspaceId, ProjectTemplateVersion version, Revision expectedRevision);
}
