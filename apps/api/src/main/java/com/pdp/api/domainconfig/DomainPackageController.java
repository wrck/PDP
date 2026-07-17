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
import com.pdp.shared.page.KeysetCursor;
import com.pdp.shared.page.SignedKeysetCursorCodec;
import com.pdp.shared.page.SortDirection;
import com.pdp.shared.page.SortOrder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/domain-packages")
public final class DomainPackageController {
  private final DomainPackageLifecycleService lifecycle;
  private final DomainPackageValidationService validation;
  private final DomainPackageMigrationService migrations;
  private final SignedKeysetCursorCodec cursors;
  private final Clock clock;
  private static final List<SortOrder> PACKAGE_SORT =
      List.of(
          new SortOrder("stableKey", SortDirection.ASCENDING, false),
          new SortOrder("id", SortDirection.ASCENDING, true));

  public DomainPackageController(
      DomainPackageLifecycleService lifecycle,
      DomainPackageValidationService validation,
      DomainPackageMigrationService migrations,
      SignedKeysetCursorCodec cursors,
      Clock clock) {
    this.lifecycle = lifecycle;
    this.validation = validation;
    this.migrations = migrations;
    this.cursors = cursors;
    this.clock = clock;
  }

  @GetMapping
  public DomainPackagePage list(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @RequestParam(required = false) PackageLayer layer,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "100") int pageSize) {
    if (pageSize < 1 || pageSize > 200) {
      throw new IllegalArgumentException("pageSize 必须在 1 到 200 之间");
    }
    String filterDigest = filterDigest(layer);
    CursorKey after =
        cursor == null || cursor.isBlank()
            ? null
            : decodeCursor(cursor, workspaceId, filterDigest);
    List<DomainPackage> candidates =
        lifecycle.list(workspaceId).stream()
            .filter(value -> layer == null || value.layer() == layer)
            .sorted(Comparator.comparing(DomainPackage::stableKey).thenComparing(DomainPackage::id))
            .filter(value -> after == null || compare(value, after) > 0)
            .limit((long) pageSize + 1)
            .toList();
    boolean hasNext = candidates.size() > pageSize;
    List<DomainPackage> pageItems =
        hasNext ? List.copyOf(candidates.subList(0, pageSize)) : candidates;
    String nextCursor =
        hasNext ? encodeCursor(pageItems.getLast(), workspaceId, filterDigest) : null;
    return new DomainPackagePage(
        pageItems.stream().map(DomainPackageResponse::from).toList(), nextCursor);
  }

  @PostMapping
  public ResponseEntity<DomainPackageResponse> create(
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
        .body(DomainPackageResponse.from(created));
  }

  @PostMapping("/{packageId}/versions")
  public ResponseEntity<DomainPackageVersionResponse> createVersion(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      Authentication authentication,
      @RequestBody CreateVersionRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    DomainPackageVersion created =
        lifecycle.createVersion(
            packageId, request.semanticVersion(), request.manifest(), actorId(authentication));
    return ResponseEntity.created(
            URI.create("/api/v1/domain-packages/" + packageId + "/versions/" + created.id()))
        .eTag(EntityTag.from(created.revision()).value())
        .body(DomainPackageVersionResponse.from(created));
  }

  @PostMapping("/{packageId}/versions/{versionId}/validate")
  public ResponseEntity<ValidationReport> validate(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch) {
    requireWorkspacePackage(workspaceId, packageId);
    DomainPackageVersion current = lifecycle.requireVersion(packageId, versionId);
    ValidationReport report = validation.validate(current.manifest());
    DomainPackageVersion result = current;
    if (report.valid()) {
      result = lifecycle.validate(packageId, versionId, EntityTag.parse(ifMatch).revision());
    }
    return ResponseEntity.ok()
        .eTag(EntityTag.from(result.revision()).value())
        .body(report);
  }

  @PostMapping("/{packageId}/versions/{versionId}/submit-review")
  public ResponseEntity<DomainPackageVersionResponse> submitReview(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication) {
    requireWorkspacePackage(workspaceId, packageId);
    return withEtag(
        lifecycle.submitReview(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            actorId(authentication)));
  }

  @PostMapping("/{packageId}/versions/{versionId}/review")
  public ResponseEntity<DomainPackageVersionResponse> review(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication,
      @RequestBody ReviewRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    DomainPackageVersion reviewed =
        lifecycle.review(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            actorId(authentication),
            request.decision() == ReviewDecision.APPROVE,
            request.comment());
    return withEtag(reviewed);
  }

  @PostMapping("/{packageId}/versions/{versionId}/publish")
  public ResponseEntity<DomainPackageVersionResponse> publish(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication,
      @RequestBody PublishRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    return withEtag(
        lifecycle.publish(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            actorId(authentication),
            request.reviewComment()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/retire")
  public ResponseEntity<DomainPackageVersionResponse> retire(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody ReasonRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    return withEtag(
        lifecycle.retire(
            packageId, versionId, EntityTag.parse(ifMatch).revision(), request.reason()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/rollback")
  public ResponseEntity<DomainPackageVersionResponse> rollback(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody RollbackRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    return withEtag(
        lifecycle.rollback(
            packageId,
            versionId,
            EntityTag.parse(ifMatch).revision(),
            request.targetVersionId(),
            request.reason()));
  }

  @PostMapping("/{packageId}/versions/{versionId}/migration-preview")
  public MigrationPreviewResponse previewMigration(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestBody PreviewMigrationRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    lifecycle.requireVersion(packageId, versionId);
    MigrationPreview preview =
        migrations.preview(
            request.sourceVersionId(),
            versionId,
            request.scope(),
            request.batchSize() == null ? 100 : request.batchSize());
    return MigrationPreviewResponse.from(preview);
  }

  @PostMapping("/{packageId}/versions/{versionId}/migrations")
  public ResponseEntity<MigrationJobResponse> startMigration(
      @RequestHeader("X-Workspace-Id") UUID workspaceId,
      @PathVariable UUID packageId,
      @PathVariable UUID versionId,
      @RequestBody StartMigrationRequest request) {
    requireWorkspacePackage(workspaceId, packageId);
    lifecycle.requireVersion(packageId, versionId);
    MigrationPreview preview = migrations.requirePreview(request.previewId());
    if (!preview.targetVersionId().equals(versionId)) {
      throw new IllegalArgumentException("迁移预览目标版本与请求路径不一致");
    }
    MigrationJob job =
        migrations.start(
            request.previewId(),
            request.confirmationToken(),
            request.batchSize(),
            request.reason());
    return ResponseEntity.accepted()
        .body(MigrationJobResponse.from(job));
  }

  private void requireWorkspacePackage(UUID workspaceId, UUID packageId) {
    boolean present =
        lifecycle.list(workspaceId).stream().anyMatch(value -> value.id().equals(packageId));
    if (!present) {
      throw new IllegalArgumentException("当前工作空间不存在该领域包");
    }
  }

  private static int compare(DomainPackage value, CursorKey cursor) {
    int stableKeyCompared = value.stableKey().compareTo(cursor.stableKey());
    return stableKeyCompared != 0 ? stableKeyCompared : value.id().compareTo(cursor.id());
  }

  private String encodeCursor(
      DomainPackage value, UUID workspaceId, String filterDigest) {
    return cursors.encode(
        new KeysetCursor(
            workspaceId.toString(),
            filterDigest,
            PACKAGE_SORT,
            List.of(value.stableKey(), value.id().toString()),
            clock.instant()));
  }

  private CursorKey decodeCursor(String cursor, UUID workspaceId, String filterDigest) {
    try {
      KeysetCursor decoded = cursors.decode(cursor, workspaceId.toString(), filterDigest);
      if (!decoded.sortOrders().equals(PACKAGE_SORT) || decoded.values().size() != 2) {
        throw new IllegalArgumentException("领域包游标无效");
      }
      return new CursorKey(decoded.values().get(0), UUID.fromString(decoded.values().get(1)));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("领域包游标无效", exception);
    }
  }

  private static String filterDigest(PackageLayer layer) {
    String canonical = "layer=" + (layer == null ? "*" : layer.name()) + "|sort=stableKey,id";
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算领域包筛选摘要", exception);
    }
  }

  private static ResponseEntity<DomainPackageVersionResponse> withEtag(
      DomainPackageVersion version) {
    return ResponseEntity.ok()
        .eTag(EntityTag.from(version.revision()).value())
        .body(DomainPackageVersionResponse.from(version));
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

  public record PublishRequest(String reviewComment) {}

  public record ReasonRequest(String reason) {}

  public record RollbackRequest(UUID targetVersionId, String reason) {}

  public record PreviewMigrationRequest(
      UUID sourceVersionId, Map<String, Object> scope, Integer batchSize) {}

  public record StartMigrationRequest(
      UUID previewId, String confirmationToken, int batchSize, String reason) {}

  public record DomainPackagePage(List<DomainPackageResponse> items, String nextCursor) {
    public DomainPackagePage {
      items = List.copyOf(items);
    }
  }

  public record DomainPackageResponse(
      UUID id,
      UUID workspaceId,
      String stableKey,
      String name,
      PackageLayer layer,
      UUID parentPackageId,
      DomainPackage.Status status,
      long revision) {
    static DomainPackageResponse from(DomainPackage domainPackage) {
      return new DomainPackageResponse(
          domainPackage.id(),
          domainPackage.workspaceId(),
          domainPackage.stableKey(),
          domainPackage.name(),
          domainPackage.layer(),
          domainPackage.parentPackageId(),
          domainPackage.status(),
          domainPackage.revision().value());
    }
  }

  public record DomainPackageVersionResponse(
      UUID id,
      UUID packageId,
      String semanticVersion,
      String contentHash,
      DomainPackageVersion.Status status,
      boolean frozen,
      long revision) {
    static DomainPackageVersionResponse from(DomainPackageVersion version) {
      return new DomainPackageVersionResponse(
          version.id(),
          version.packageId(),
          version.semanticVersion(),
          version.contentHash(),
          version.status(),
          version.frozen(),
          version.revision().value());
    }
  }

  public record MigrationPreviewResponse(
      UUID previewId,
      long affectedInstances,
      List<String> conflicts,
      int batches,
      boolean rollbackAvailable,
      String confirmationToken) {
    static MigrationPreviewResponse from(MigrationPreview preview) {
      return new MigrationPreviewResponse(
          preview.id(),
          preview.affectedInstances(),
          preview.conflicts(),
          preview.batches(),
          preview.rollbackAvailable(),
          preview.impact().confirmationToken());
    }
  }

  public record MigrationJobResponse(
      UUID id,
      UUID sourceVersionId,
      UUID targetVersionId,
      String status,
      long migrated,
      long failed,
      long revision) {
    static MigrationJobResponse from(MigrationJob job) {
      return new MigrationJobResponse(
          job.id(),
          job.sourceVersionId(),
          job.targetVersionId(),
          job.status().name(),
          job.migrated(),
          job.failed(),
          job.revision().value());
    }
  }

  private record CursorKey(String stableKey, UUID id) {}
}
