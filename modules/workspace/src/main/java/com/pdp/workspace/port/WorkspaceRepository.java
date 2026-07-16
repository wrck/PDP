package com.pdp.workspace.port;

import com.pdp.workspace.domain.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {
  Optional<Workspace> findById(UUID id);

  Optional<Workspace> findByCode(String code);

  List<Workspace> findAccessibleByUserId(UUID userId);

  Workspace save(Workspace workspace);
}
