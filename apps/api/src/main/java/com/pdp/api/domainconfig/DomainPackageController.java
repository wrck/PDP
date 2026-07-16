package com.pdp.api.domainconfig;

import com.pdp.domainconfig.application.DomainPackageLifecycleService;
import com.pdp.domainconfig.application.DomainPackageMigrationService;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationJob;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationPreview;
import com.pdp.domainconfig.application.DomainPackageValidationService;
import com.pdp.domainconfig.application.DomainPackageValidationService.ValidationReport;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.identity.application.AuthenticatedActor;
import com.pdp.shared.concurrency.EntityTag;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/domain-packages")
public final class DomainPackageController {
  private final DomainPackageLifecycleService lifecycle;
  private final DomainPackageValidationService validation;
  private final DomainPackageMigrationService migrations;

  public DomainPackageController(
      DomainPackageLifecycleService lifecycle,
      DomainPackageValidationService validation,
      DomainPackageMigrationService migrations) {
    this.lifecycle = lifecycle;
    this.validation = validation;
    this.migrations = migrations;
  }

  @GetMapping
  public List<DomainPackage> list(@RequestHeader("X-Workspace-Id") UUID workspaceId) {
    return lifecycle.list(workspaceId);
  }

  @PostMapping
  public ResponseEntity<DomainPackage> create(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @RequestBody CreateDomainPackageRequest request) {
    DomainPackage created =
        lifecycle.createPackage(
            workspaceId,
            request.stableKey(),
            request.name(),
            request.layer(),
            request.parentPackageId());
    return ResponseEntity.created(URI.create("/api/v1/domain-packages/" + created.id()))
        .eTag(EntityTag.from(created.revision()).value())
        .body(created);
  }

  @PostMapping("/{packageId}/versions")
  public ResponseEntity<DomainPackageVersion> createVersion(
      @PathVariable UUID packageId,
      Authentication authentication,
      @RequestBody CreateVersionRequest request) {
    DomainPackageVersion created =
        lifecycle.createVersion(
            packageId, request.semanticVersion(), request.manifest(), actorId(authentication));
    return ResponseEntity.created(
            URI.create("/api/v1/domain-packages/" + packageId + "/versions/" + created.id()))
        .eTag(EntityTag.from(created.revision()).value())
        .body(created);
  }

  @PostMapping("/{packageId}/versions/{versionId}/validate")
  public ResponseEntity<ValidationReport> validate(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch) {
    DomainPackageVersion current = lifecycle.requireVersion(packageId, versionId);
    ValidationReport report = validation.validate(current.manifest());
    if (report.valid()) {
      lifecycle.validate(packageId, versionId, EntityTag.parse(ifMatch).revision());
    }
    return ResponseEntity.ok(report);
  }

  @PostMapping("/{packageId}/versions/{versionId}/review")
  public ResponseEntity<DomainPackageVersion> review(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication,
      @RequestBody ReviewRequest request) {
    DomainPackageVersion current = lifecycle.requireVersion(packageId, versionId);
    if (current.status() == DomainPackageVersion.Status.VALIDATED) {
      current =
          lifecycle.submitReview(
              packageId, versionId, EntityTag.parse(ifMatch).revision(), current.createdBy());
    }
    DomainPackageVersion reviewed =
        lifecycle.review(
            packageId,
            versionId,
            current.revision(),
            actorId(authentication),
            request.decision() == ReviewDecision.APPROVE,
            request.comment());
    return withEtag(reviewed);
  }

  @PostMapping("/{packageId}/versions/{versionId}/publish")
  public ResponseEntity<DomainPackageVersion> publish(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication,
      @RequestBody ReasonRequest request) {
    return withEtag(
        lifecycle.publish(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            actorId(authentication),
            request.reason()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/retire")
  public ResponseEntity<DomainPackageVersion> retire(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody ReasonRequest request) {
    return withEtag(
        lifecycle.retire(
            packageId, versionId, EntityTag.parse(ifMatch).revision(), request.reason()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/rollback")
  public ResponseEntity<DomainPackageVersion> rollback(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody RollbackRequest request) {
    return withEtag(
        lifecycle.rollback(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            request.targetVersionId(),
            request.reason()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/migration-preview")
  public MigrationPreview previewMigration(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestBody PreviewMigrationRequest request) {
    lifecycle.requireVersion(packageId, versionId);
    return migrations.preview(
        request.sourceVersionId(),
        versionId,
        request.scope(),
        request.batchSize() == null ? 100 : request.batchSize());
  }

  @PostMapping("/{packageId}/versions/{versionId}/migrations")
  public ResponseEntity<MigrationJob> startMigration(
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestBody StartMigrationRequest request) {
    lifecycle.requireVersion(packageId, versionId);
    return ResponseEntity.accepted()
        .body(
            migrations.start(
                request.previewId(),
                request.confirmationToken(),
                request.batchSize(),
                request.reason()));
  }

  private static ResponseEntity<DomainPackageVersion> withEtag(DomainPackageVersion version) {
    return ResponseEntity.ok()
        .eTag(EntityTag.from(version.revision()).value())
        .body(version);
  }

  private static UUID actorId(Authentication authentication) {
    if (authentication.getPrincipal() instanceof AuthenticatedActor actor) {
      return actor.userId();
    }
    return UUID.fromString(authentication.getName());
  }

  public record CreateDomainPackageRequest(
      String stableKey, String name, PackageLayer layer, UUID parentPackageId) {}

  public record CreateVersionRequest(
      String semanticVersion, DomainPackageManifest manifest, String changeSummary) {}

  public enum ReviewDecision {
    APPROVE,
    REJECT
  }

  public record ReviewRequest(ReviewDecision decision, String comment) {}

  public record ReasonRequest(String reason) {}

  public record RollbackRequest(UUID targetVersionId, String reason) {}

  public record PreviewMigrationRequest(
      UUID sourceVersionId, Map<String, Object> scope, Integer batchSize) {}

  public record StartMigrationRequest(
      UUID previewId, String confirmationToken, int batchSize, String reason) {}
}
