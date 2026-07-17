package com.pdp.project.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 项目成员的有效期和来源是工作空间授权复核的输入。 */
public record ProjectMember(UUID id, UUID projectId, UUID actorId, String projectRoleKey,
                            Instant validFrom, Instant validUntil, String dataScope, Source source,
                            Revision revision) {
  public enum Source { WORKSPACE_MEMBERSHIP, COLLABORATION_GRANT, EXTERNAL_AUTHORIZATION }
  public ProjectMember {
    Objects.requireNonNull(id, "成员 id 不能为空"); Objects.requireNonNull(projectId, "成员项目不能为空");
    Objects.requireNonNull(actorId, "成员主体不能为空"); Objects.requireNonNull(validFrom, "成员生效时间不能为空");
    Objects.requireNonNull(source, "成员来源不能为空"); Objects.requireNonNull(revision, "成员 revision 不能为空");
    if (projectRoleKey == null || projectRoleKey.isBlank()) throw new IllegalArgumentException("项目角色不能为空");
    projectRoleKey = projectRoleKey.trim();
    if (validUntil != null && !validUntil.isAfter(validFrom)) throw new IllegalArgumentException("成员失效时间必须晚于生效时间");
  }
  public boolean activeAt(Instant at) { return !at.isBefore(validFrom) && (validUntil == null || at.isBefore(validUntil)); }
}
