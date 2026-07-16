package com.pdp.identity.application;

import com.pdp.shared.context.ActorId;
import com.pdp.shared.context.WorkspaceId;

@FunctionalInterface
public interface WorkspaceBoundaryVerifier {
  void requireWorkspaceAccess(ActorId actorId, WorkspaceId workspaceId);
}
