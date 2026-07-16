package com.pdp.workspace.port;

import java.util.UUID;

@FunctionalInterface
public interface WorkspaceArchiveReadinessPort {
  boolean canArchive(UUID workspaceId);
}
