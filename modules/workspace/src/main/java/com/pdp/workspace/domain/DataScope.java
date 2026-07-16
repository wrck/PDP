package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record DataScope(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    Set<String> resourceTypes,
    Map<String, Object> condition,
    Status status,
    Revision revision) {
  public enum Status {
    ACTIVE,
    RETIRED
  }

  public DataScope {
    resourceTypes = Set.copyOf(resourceTypes);
    condition = Map.copyOf(condition);
    if (stableKey == null || stableKey.isBlank() || name == null || name.isBlank()) {
      throw new IllegalArgumentException("数据范围键和名称不能为空");
    }
    if (resourceTypes.isEmpty()) {
      throw new IllegalArgumentException("数据范围至少需要一个资源类型");
    }
  }

  public static DataScope create(
      UUID workspaceId,
      String stableKey,
      String name,
      Set<String> resourceTypes,
      Map<String, Object> condition) {
    return new DataScope(
        UUID.randomUUID(),
        workspaceId,
        stableKey,
        name,
        resourceTypes,
        condition,
        Status.ACTIVE,
        new Revision(0));
  }
}
