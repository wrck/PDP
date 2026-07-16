package com.pdp.domainconfig.domain.packageversion;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DomainPackageVersion(
    UUID id,
    UUID packageId,
    String semanticVersion,
    DomainPackageManifest manifest,
    String contentHash,
    Status status,
    boolean frozen,
    UUID createdBy,
    UUID reviewedBy,
    UUID approvedBy,
    Revision revision,
    Instant createdAt) {

  public enum Status {
    DRAFT,
    VALIDATED,
    REVIEW_PENDING,
    APPROVED,
    PUBLISHED,
    RETIRED,
    ROLLED_BACK
  }

  public DomainPackageVersion {
    Objects.requireNonNull(id);
    Objects.requireNonNull(packageId);
    Objects.requireNonNull(manifest);
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdBy);
    Objects.requireNonNull(revision);
    Objects.requireNonNull(createdAt);
    if (semanticVersion == null || !semanticVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
      throw new IllegalArgumentException("领域包版本必须使用语义化版本");
    }
  }

  public static DomainPackageVersion draft(
      UUID packageId,
      String semanticVersion,
      DomainPackageManifest manifest,
      String contentHash,
      UUID createdBy,
      Instant at) {
    return new DomainPackageVersion(
        UUID.randomUUID(),
        packageId,
        semanticVersion,
        manifest,
        contentHash,
        Status.DRAFT,
        false,
        createdBy,
        null,
        null,
        new Revision(0),
        at);
  }

  public DomainPackageVersion validate() {
    requireStatus(Status.DRAFT);
    return transition(Status.VALIDATED, false, reviewedBy, approvedBy);
  }

  public DomainPackageVersion submitReview() {
    requireStatus(Status.VALIDATED);
    return transition(Status.REVIEW_PENDING, false, reviewedBy, approvedBy);
  }

  public DomainPackageVersion approve(UUID reviewer) {
    requireStatus(Status.REVIEW_PENDING);
    if (createdBy.equals(reviewer)) {
      throw new IllegalStateException("领域包创建者不得审核自己的版本");
    }
    return transition(Status.APPROVED, false, reviewer, approvedBy);
  }

  public DomainPackageVersion publish(UUID approver) {
    requireStatus(Status.APPROVED);
    if (createdBy.equals(approver) || approver.equals(reviewedBy)) {
      throw new IllegalStateException("创建、审核和发布批准必须职责分离");
    }
    return transition(Status.PUBLISHED, true, reviewedBy, approver);
  }

  public DomainPackageVersion retire() {
    requireStatus(Status.PUBLISHED);
    return transition(Status.RETIRED, true, reviewedBy, approvedBy);
  }

  public DomainPackageVersion rollback() {
    if (status != Status.PUBLISHED && status != Status.RETIRED) {
      throw new IllegalStateException("仅已发布或已退役版本可以回滚");
    }
    return transition(Status.ROLLED_BACK, true, reviewedBy, approvedBy);
  }

  private void requireStatus(Status expected) {
    if (status != expected) {
      throw new IllegalStateException("领域包版本状态不允许执行该操作: " + status);
    }
  }

  private DomainPackageVersion transition(
      Status next, boolean nextFrozen, UUID nextReviewedBy, UUID nextApprovedBy) {
    return new DomainPackageVersion(
        id,
        packageId,
        semanticVersion,
        manifest,
        contentHash,
        next,
        nextFrozen,
        createdBy,
        nextReviewedBy,
        nextApprovedBy,
        revision.next(),
        createdAt);
  }
}
