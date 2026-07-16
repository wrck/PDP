package com.pdp.workspace.port;

import com.pdp.workspace.domain.WorkspaceMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository {
  Optional<WorkspaceMembership> findMembershipById(UUID id);

  Optional<WorkspaceMembership> findByWorkspaceAndUser(UUID workspaceId, UUID userId);

  List<WorkspaceMembership> findMembershipsByWorkspaceId(UUID workspaceId);

  WorkspaceMembership save(WorkspaceMembership membership);
}
