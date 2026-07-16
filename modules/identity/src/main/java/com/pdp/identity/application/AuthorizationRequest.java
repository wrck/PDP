package com.pdp.identity.application;

import com.pdp.shared.context.ActorId;
import com.pdp.shared.context.WorkspaceId;

public record AuthorizationRequest(
    ActorId actorId, WorkspaceId workspaceId, String action, ResourceDescriptor resource) {
  public AuthorizationRequest {
    if (actorId == null || workspaceId == null || resource == null) {
      throw new IllegalArgumentException("授权请求上下文不能为空");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("授权动作不能为空");
    }
  }
}
