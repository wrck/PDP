package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.port.DomainPackageInstanceInventoryPort;
import com.pdp.domainconfig.port.DomainPackageMigrationRepository;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.operation.HighRiskOperationType;
import com.pdp.shared.operation.OperationConfirmation;
import com.pdp.shared.operation.OperationConfirmationPort;
import com.pdp.shared.operation.OperationImpactPreview;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class DomainPackageMigrationService {
  private final DomainPackageVersionRepository versions;
  private final DomainPackageInstanceInventoryPort inventory;
  private final DomainPackageMigrationRepository migrations;
  private final OperationConfirmationPort confirmations;
  private final Clock clock;

  public DomainPackageMigrationService(
      DomainPackageVersionRepository versions,
      DomainPackageInstanceInventoryPort inventory,
      DomainPackageMigrationRepository migrations,
      OperationConfirmationPort confirmations,
      Clock clock) {
    this.versions = versions;
    this.inventory = inventory;
    this.migrations = migrations;
    this.confirmations = confirmations;
    this.clock = clock;
  }

  public MigrationPreview preview(
      UUID sourceVersionId, UUID targetVersionId, Object scope, int batchSize) {
    DomainPackageVersion source = requirePublished(sourceVersionId);
    DomainPackageVersion target = requirePublished(targetVersionId);
    if (!source.packageId().equals(target.packageId())) {
      throw new IllegalArgumentException("实例迁移源目标版本必须属于同一领域包");
    }
    if (batchSize < 1 || batchSize > 1000) {
      throw new IllegalArgumentException("迁移批次大小必须在 1 到 1000 之间");
    }
    long affected = inventory.countInstances(sourceVersionId, scope);
    int batches = affected == 0 ? 0 : Math.toIntExact((affected + batchSize - 1) / batchSize);
    UUID previewId = UUID.randomUUID();
    String commandDigest =
        DomainPackageCompositionService.sha256(sourceVersionId + "|" + targetVersionId + "|" + scope);
    String revisionDigest =
        DomainPackageCompositionService.sha256(source.revision() + "|" + target.revision());
    Instant previewedAt = clock.instant();
    String token =
        confirmations.issue(
            new OperationConfirmation(
                previewId,
                HighRiskOperationType.DOMAIN_PACKAGE_MIGRATION,
                commandDigest,
                revisionDigest,
                previewedAt));
    var impact =
        new OperationImpactPreview(
            previewId,
            HighRiskOperationType.DOMAIN_PACKAGE_MIGRATION,
            Map.of("instances", affected, "batches", (long) batches),
            affected == 0 ? List.of("没有需要迁移的实例") : List.of(),
            "首个实例写入目标版本后",
            "按迁移快照恢复源版本",
            token,
            previewedAt.plusSeconds(900));
    return migrations.savePreview(
        new MigrationPreview(
            previewId,
            sourceVersionId,
            targetVersionId,
            affected,
            batches,
            List.of(),
            true,
            commandDigest,
            revisionDigest,
            impact));
  }

  public MigrationJob start(UUID previewId, String confirmationToken, int batchSize, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("迁移原因不能为空");
    }
    MigrationPreview preview =
        migrations.findPreview(previewId).orElseThrow(() -> new IllegalArgumentException("迁移预览不存在"));
    confirmations.verify(
        confirmationToken,
        new OperationConfirmation(
            preview.id(),
            HighRiskOperationType.DOMAIN_PACKAGE_MIGRATION,
            preview.commandDigest(),
            preview.revisionDigest(),
            preview.impact().expiresAt().minusSeconds(900)));
    var job =
        new MigrationJob(
            UUID.randomUUID(),
            preview.id(),
            preview.sourceVersionId(),
            preview.targetVersionId(),
            Status.PLANNED,
            batchSize,
            0,
            0,
            List.of(),
            new Revision(0),
            clock.instant());
    return migrations.saveJob(job);
  }

  public MigrationJob recordBatch(UUID jobId, int migrated, List<UUID> failedInstances) {
    MigrationJob current =
        migrations.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("迁移作业不存在"));
    Status status =
        failedInstances.isEmpty() ? Status.RUNNING : Status.PARTIALLY_FAILED;
    return migrations.saveJob(
        new MigrationJob(
            current.id(),
            current.previewId(),
            current.sourceVersionId(),
            current.targetVersionId(),
            status,
            current.batchSize(),
            current.migrated() + migrated,
            current.failed() + failedInstances.size(),
            failedInstances,
            current.revision().next(),
            current.createdAt()));
  }

  public MigrationJob rollback(UUID jobId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("回滚原因不能为空");
    }
    MigrationJob current =
        migrations.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("迁移作业不存在"));
    return migrations.saveJob(
        new MigrationJob(
            current.id(),
            current.previewId(),
            current.sourceVersionId(),
            current.targetVersionId(),
            Status.ROLLED_BACK,
            current.batchSize(),
            current.migrated(),
            current.failed(),
            current.failedInstances(),
            current.revision().next(),
            current.createdAt()));
  }

  private DomainPackageVersion requirePublished(UUID versionId) {
    return versions
        .findVersionById(versionId)
        .filter(value -> value.status() == DomainPackageVersion.Status.PUBLISHED)
        .orElseThrow(() -> new IllegalArgumentException("迁移版本必须已发布"));
  }

  public enum Status {
    PLANNED,
    RUNNING,
    PARTIALLY_FAILED,
    COMPLETED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED
  }

  public record MigrationPreview(
      UUID id,
      UUID sourceVersionId,
      UUID targetVersionId,
      long affectedInstances,
      int batches,
      List<String> conflicts,
      boolean rollbackAvailable,
      String commandDigest,
      String revisionDigest,
      OperationImpactPreview impact) {
    public MigrationPreview {
      conflicts = List.copyOf(conflicts);
    }
  }

  public record MigrationJob(
      UUID id,
      UUID previewId,
      UUID sourceVersionId,
      UUID targetVersionId,
      Status status,
      int batchSize,
      long migrated,
      long failed,
      List<UUID> failedInstances,
      Revision revision,
      Instant createdAt) {
    public MigrationJob {
      if (previewId == null) {
        throw new IllegalArgumentException("迁移作业必须关联预览标识");
      }
      failedInstances = List.copyOf(failedInstances);
    }
  }
}
