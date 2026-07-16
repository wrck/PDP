package com.pdp.domainconfig.domain.packageversion;

import com.pdp.shared.concurrency.Revision;
import java.util.Objects;
import java.util.UUID;

public record DomainPackage(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    PackageLayer layer,
    UUID parentPackageId,
    Status status,
    Revision revision) {

  public enum Status {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    RETIRED
  }

  public DomainPackage {
    Objects.requireNonNull(id);
    Objects.requireNonNull(workspaceId);
    Objects.requireNonNull(layer);
    Objects.requireNonNull(status);
    Objects.requireNonNull(revision);
    stableKey = requireText(stableKey, "稳定标识");
    name = requireText(name, "领域包名称");
    if (layer != PackageLayer.PLATFORM_STANDARD && parentPackageId == null) {
      throw new IllegalArgumentException("行业包和工作空间客户包必须声明父领域包");
    }
  }

  public static DomainPackage draft(
      UUID workspaceId,
      String stableKey,
      String name,
      PackageLayer layer,
      UUID parentPackageId) {
    return new DomainPackage(
        UUID.randomUUID(),
        workspaceId,
        stableKey,
        name,
        layer,
        parentPackageId,
        Status.DRAFT,
        new Revision(0));
  }

  public DomainPackage activate() {
    return new DomainPackage(
        id, workspaceId, stableKey, name, layer, parentPackageId, Status.ACTIVE, revision.next());
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    return value;
  }
}
