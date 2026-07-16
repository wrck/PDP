package com.pdp.workspace.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** 查询、搜索、导出和附件访问共用的跨工作空间隔离守卫。 */
@Service
public final class WorkspaceIsolationGuard {
  private final CollaborationGrantService collaborationGrants;

  public WorkspaceIsolationGuard(CollaborationGrantService collaborationGrants) {
    this.collaborationGrants = collaborationGrants;
  }

  public void requireAccess(
      UUID ownerWorkspaceId,
      UUID requesterWorkspaceId,
      TargetReference target,
      String action) {
    if (ownerWorkspaceId.equals(requesterWorkspaceId)) {
      return;
    }
    if (!collaborationGrants.isAllowed(
        ownerWorkspaceId, requesterWorkspaceId, target, action)) {
      throw new WorkspaceIsolationException();
    }
  }
}
