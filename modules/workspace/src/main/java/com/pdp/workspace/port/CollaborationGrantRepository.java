package com.pdp.workspace.port;

import com.pdp.workspace.domain.CollaborationGrant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaborationGrantRepository {
  Optional<CollaborationGrant> findGrantById(UUID id);

  List<CollaborationGrant> findGrantsByOwnerWorkspaceId(UUID workspaceId);

  List<CollaborationGrant> findActiveGrants(
      UUID ownerWorkspaceId, UUID collaboratorWorkspaceId, Instant at);

  CollaborationGrant save(CollaborationGrant grant);
}
