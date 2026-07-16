package com.pdp.shared.context;

import java.util.Objects;
import java.util.UUID;

public record WorkspaceId(UUID value) {
  public WorkspaceId {
    Objects.requireNonNull(value, "workspaceId 不能为空");
  }
}
