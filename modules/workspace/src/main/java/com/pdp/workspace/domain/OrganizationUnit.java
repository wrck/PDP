package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.util.Objects;
import java.util.UUID;

public record OrganizationUnit(
    UUID id,
    UUID workspaceId,
    UUID parentId,
    String code,
    String name,
    Type type,
    String regionCode,
    String path,
    Status status,
    Revision revision) {

  public enum Type {
    COMPANY,
    DEPARTMENT,
    TEAM,
    REGION,
    EXTERNAL
  }

  public enum Status {
    ACTIVE,
    DISABLED
  }

  public OrganizationUnit {
    Objects.requireNonNull(id);
    Objects.requireNonNull(workspaceId);
    if (code == null || code.isBlank() || name == null || name.isBlank()) {
      throw new IllegalArgumentException("组织编码和名称不能为空");
    }
    Objects.requireNonNull(type);
    if (path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("组织路径无效");
    }
    Objects.requireNonNull(status);
    Objects.requireNonNull(revision);
  }

  public static OrganizationUnit create(
      UUID workspaceId,
      OrganizationUnit parent,
      String code,
      String name,
      Type type,
      String regionCode) {
    if (parent != null && !parent.workspaceId().equals(workspaceId)) {
      throw new IllegalArgumentException("父组织必须属于同一工作空间");
    }
    String path = parent == null ? "/" + code : parent.path() + "/" + code;
    return new OrganizationUnit(
        UUID.randomUUID(),
        workspaceId,
        parent == null ? null : parent.id(),
        code,
        name,
        type,
        regionCode,
        path,
        Status.ACTIVE,
        new Revision(0));
  }
}
