package com.pdp.workspace.port;

import com.pdp.workspace.domain.DataScope;
import java.util.Optional;
import java.util.UUID;

public interface DataScopeRepository {
  Optional<DataScope> findDataScopeById(UUID id);

  Optional<DataScope> findDataScopeByStableKey(UUID workspaceId, String stableKey);

  DataScope save(DataScope dataScope);
}
