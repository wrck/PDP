package com.pdp.workspace.port;

import com.pdp.workspace.domain.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRoleRepository {
  Optional<WorkspaceRole> findRoleById(UUID id);

  Optional<WorkspaceRole> findRoleByStableKey(UUID workspaceId, String stableKey);

  List<WorkspaceRole> findRolesByWorkspaceId(UUID workspaceId);

  WorkspaceRole save(WorkspaceRole role);
}
