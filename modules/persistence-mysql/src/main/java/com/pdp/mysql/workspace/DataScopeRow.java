package com.pdp.mysql.workspace;

import com.pdp.persistence.type.JsonDocument;
import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.DataScope;
import java.util.UUID;

public record DataScopeRow(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    JsonDocument resourceTypes,
    JsonDocument conditionDefinition,
    DataScope.Status status,
    long revision) {

  static DataScopeRow fromDomain(DataScope scope) {
    return new DataScopeRow(
        scope.id(),
        scope.workspaceId(),
        scope.stableKey(),
        scope.name(),
        WorkspaceJsonCodec.stringSet(scope.resourceTypes()),
        WorkspaceJsonCodec.map(scope.condition()),
        scope.status(),
        scope.revision().value());
  }

  DataScope toDomain() {
    return new DataScope(
        id,
        workspaceId,
        stableKey,
        name,
        WorkspaceJsonCodec.stringSet(resourceTypes),
        WorkspaceJsonCodec.map(conditionDefinition),
        status,
        new Revision(revision));
  }
}
