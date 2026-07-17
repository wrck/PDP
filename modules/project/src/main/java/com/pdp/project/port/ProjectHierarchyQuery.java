package com.pdp.project.port;

import java.util.List;
import java.util.UUID;

/** 父子关系查询端口，供应用层在写入前拒绝环形层级。 */
public interface ProjectHierarchyQuery {
  boolean wouldCreateCycle(UUID workspaceId, UUID projectId, UUID proposedParentProjectId);
  List<UUID> findAncestorIds(UUID workspaceId, UUID projectId);
}
