package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.domainconfig.port.DomainPackageEventPublisher;
import com.pdp.domainconfig.port.DomainPackageEventPublisher.DomainPackageEvent;
import com.pdp.domainconfig.port.DomainPackageRepository;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import com.pdp.shared.concurrency.OptimisticConcurrencyGuard;
import com.pdp.shared.concurrency.Revision;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class DomainPackageLifecycleService {
  private final DomainPackageRepository packages;
  private final DomainPackageVersionRepository versions;
  private final DomainPackageValidationService validation;
  private final DomainPackageEventPublisher events;
  private final Clock clock;

  public DomainPackageLifecycleService(
      DomainPackageRepository packages,
      DomainPackageVersionRepository versions,
      DomainPackageValidationService validation,
      DomainPackageEventPublisher events,
      Clock clock) {
    this.packages = packages;
    this.versions = versions;
    this.validation = validation;
    this.events = events;
    this.clock = clock;
  }

  public DomainPackage createPackage(
      UUID workspaceId,
      String stableKey,
      String name,
      PackageLayer layer,
      UUID parentPackageId) {
    if (packages.findByStableKey(workspaceId, stableKey).isPresent()) {
      throw new IllegalStateException("领域包稳定标识已存在");
    }
    if (parentPackageId != null) {
      packages.findById(parentPackageId).orElseThrow(() -> new IllegalArgumentException("父领域包不存在"));
    }
    return packages.save(DomainPackage.draft(workspaceId, stableKey, name, layer, parentPackageId));
  }

  public List<DomainPackage> list(UUID workspaceId) {
    return packages.findByWorkspace(workspaceId);
  }

  public DomainPackageVersion createVersion(
      UUID packageId,
      String semanticVersion,
      DomainPackageManifest manifest,
      UUID creator) {
    requirePackage(packageId);
    if (versions.findByPackageAndSemanticVersion(packageId, semanticVersion).isPresent()) {
      throw new IllegalStateException("领域包语义化版本已存在");
    }
    var version =
        DomainPackageVersion.draft(
            packageId,
            semanticVersion,
            manifest,
            DomainPackageCompositionService.sha256(manifest),
            creator,
            clock.instant());
    return versions.save(version);
  }

  public DomainPackageVersion validate(UUID packageId, UUID versionId, Revision expectedRevision) {
    var current = requireVersion(packageId, versionId, expectedRevision);
    var report = validation.validate(current.manifest());
    if (!report.valid()) {
      throw new IllegalStateException("领域包校验失败: " + String.join("; ", report.errors()));
    }
    return saveAndPublish(current.validate(), "pdp.domain-package.validated", "校验通过");
  }

  public DomainPackageVersion submitReview(
      UUID packageId, UUID versionId, Revision expectedRevision, UUID actorId) {
    var current = requireVersion(packageId, versionId, expectedRevision);
    if (!current.createdBy().equals(actorId)) {
      throw new IllegalStateException("仅版本创建者可以提交审核");
    }
    return saveAndPublish(current.submitReview(), "pdp.domain-package.review-requested", "提交审核");
  }

  public DomainPackageVersion review(
      UUID packageId,
      UUID versionId,
      Revision expectedRevision,
      UUID reviewer,
      boolean approved,
      String comment) {
    if (comment == null || comment.isBlank()) {
      throw new IllegalArgumentException("审核意见不能为空");
    }
    var current = requireVersion(packageId, versionId, expectedRevision);
    if (!approved) {
      throw new IllegalStateException("拒绝后应创建新草稿版本，不得原位修改");
    }
    return saveAndPublish(current.approve(reviewer), "pdp.domain-package.approved", comment);
  }

  public DomainPackageVersion publish(
      UUID packageId,
      UUID versionId,
      Revision expectedRevision,
      UUID approver,
      String reason) {
    var current = requireVersion(packageId, versionId, expectedRevision);
    var published =
        saveAndPublish(current.publish(approver), "pdp.domain-package.published", requireReason(reason));
    DomainPackage domainPackage = requirePackage(packageId);
    if (domainPackage.status() == DomainPackage.Status.DRAFT) {
      packages.save(domainPackage.activate());
    }
    return published;
  }

  public DomainPackageVersion retire(
      UUID packageId, UUID versionId, Revision expectedRevision, String reason) {
    return saveAndPublish(
        requireVersion(packageId, versionId, expectedRevision).retire(),
        "pdp.domain-package.retired",
        requireReason(reason));
  }

  public DomainPackageVersion rollback(
      UUID packageId, UUID versionId, Revision expectedRevision, UUID targetVersionId, String reason) {
    var target =
        versions
            .findVersionById(targetVersionId)
            .filter(value -> value.packageId().equals(packageId))
            .filter(value -> value.status() == DomainPackageVersion.Status.PUBLISHED)
            .orElseThrow(() -> new IllegalArgumentException("回滚目标必须是同一领域包的已发布版本"));
    var rolledBack = requireVersion(packageId, versionId, expectedRevision).rollback();
    saveAndPublish(rolledBack, "pdp.domain-package.rolled-back", requireReason(reason));
    events.publish(
        new DomainPackageEvent(
            "pdp.domain-package.rollback-target-activated",
            packageId,
            target.id(),
            target.revision().value(),
            reason,
            clock.instant()));
    return rolledBack;
  }

  public DomainPackageVersion requireVersion(UUID packageId, UUID versionId) {
    return versions
        .findVersionById(versionId)
        .filter(value -> value.packageId().equals(packageId))
        .orElseThrow(() -> new IllegalArgumentException("领域包版本不存在"));
  }

  private DomainPackage requirePackage(UUID packageId) {
    return packages.findById(packageId).orElseThrow(() -> new IllegalArgumentException("领域包不存在"));
  }

  private DomainPackageVersion requireVersion(
      UUID packageId, UUID versionId, Revision expectedRevision) {
    var version = requireVersion(packageId, versionId);
    OptimisticConcurrencyGuard.requireMatch(expectedRevision, version.revision());
    return version;
  }

  private DomainPackageVersion saveAndPublish(
      DomainPackageVersion next, String eventType, String reason) {
    var saved = versions.save(next);
    events.publish(
        new DomainPackageEvent(
            eventType,
            saved.packageId(),
            saved.id(),
            saved.revision().value(),
            reason,
            clock.instant()));
    return saved;
  }

  private static String requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("操作原因不能为空");
    }
    return reason;
  }
}
