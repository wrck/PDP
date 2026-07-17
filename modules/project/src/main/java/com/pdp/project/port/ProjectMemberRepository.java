package com.pdp.project.port;

import com.pdp.project.domain.ProjectMember;
import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 项目成员端口允许权限层在访问时按有效期复核成员资格。 */
public interface ProjectMemberRepository {
  List<ProjectMember> findActiveByProjectId(UUID workspaceId, UUID projectId, Instant at);
  ProjectMember insert(ProjectMember member);
  ProjectMember update(ProjectMember member, Revision expectedRevision);
}
