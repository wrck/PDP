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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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
        DomainPackageCompositionService.sha256(
            sourceVersionId
                + "|"
                + targetVersionId
                + "|"
                + canonicalizeScope(scope)
                + "|"
                + batchSize);
    String revisionDigest = revisionDigest(source, target, batchSize);
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
    requireBatchSize(batchSize);
    requireReason(reason, "迁移原因");
    MigrationPreview preview =
        migrations.findPreview(previewId).orElseThrow(() -> new IllegalArgumentException("迁移预览不存在"));
    if (!clock.instant().isBefore(preview.impact().expiresAt())) {
      throw new IllegalStateException("迁移预览已过期，请重新预览");
    }
    DomainPackageVersion source = requirePublished(preview.sourceVersionId());
    DomainPackageVersion target = requirePublished(preview.targetVersionId());
    if (!source.packageId().equals(target.packageId())) {
      throw new IllegalStateException("迁移预览的源目标版本不再属于同一领域包");
    }
    if (!revisionDigest(source, target, batchSize).equals(preview.revisionDigest())) {
      throw new IllegalStateException("迁移批次大小或领域包版本已在预览后变化，请重新预览");
    }
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
            preview.affectedInstances() == 0 ? Status.COMPLETED : Status.PLANNED,
            batchSize,
            0,
            0,
            List.of(),
            new Revision(0),
            clock.instant());
    return migrations.saveJob(job);
  }

  public MigrationJob recordBatch(UUID jobId, int migrated, List<UUID> failedInstances) {
    Objects.requireNonNull(failedInstances, "failedInstances");
    MigrationJob current =
        migrations.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("迁移作业不存在"));
    if (current.status() != Status.PLANNED
        && current.status() != Status.RUNNING
        && current.status() != Status.PARTIALLY_FAILED) {
      throw new IllegalStateException("当前迁移作业状态不允许记录批次: " + current.status());
    }
    var currentBatchFailures = new LinkedHashSet<>(failedInstances);
    if (currentBatchFailures.contains(null)) {
      throw new IllegalArgumentException("失败实例标识不能为空");
    }
    if (migrated < 0 || migrated + currentBatchFailures.size() < 1) {
      throw new IllegalArgumentException("迁移批次必须至少处理一个实例");
    }
    if (migrated + currentBatchFailures.size() > current.batchSize()) {
      throw new IllegalArgumentException("迁移批次处理数量不得超过批次大小");
    }
    if (currentBatchFailures.stream().anyMatch(current.failedInstances()::contains)) {
      throw new IllegalArgumentException("失败实例不得跨批次重复登记");
    }
    MigrationPreview preview =
        migrations
            .findPreview(current.previewId())
            .orElseThrow(() -> new IllegalStateException("迁移作业关联的预览不存在"));
    long processedBefore = current.migrated() + current.failed();
    long processedAfter = processedBefore + migrated + currentBatchFailures.size();
    if (processedAfter > preview.affectedInstances()) {
      throw new IllegalArgumentException("累计处理数量不得超过预览影响实例数");
    }
    var allFailures = new ArrayList<>(current.failedInstances());
    currentBatchFailures.stream().filter(value -> !allFailures.contains(value)).forEach(allFailures::add);
    Status status =
        processedAfter == preview.affectedInstances() && allFailures.isEmpty()
            ? Status.COMPLETED
            : allFailures.isEmpty() ? Status.RUNNING : Status.PARTIALLY_FAILED;
    return migrations.saveJob(
        new MigrationJob(
            current.id(),
            current.previewId(),
            current.sourceVersionId(),
            current.targetVersionId(),
            status,
            current.batchSize(),
            current.migrated() + migrated,
            current.failed() + currentBatchFailures.size(),
            allFailures,
            current.revision().next(),
            current.createdAt()));
  }

  public MigrationJob rollback(UUID jobId, String reason) {
    requireReason(reason, "回滚原因");
    MigrationJob current =
        migrations.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("迁移作业不存在"));
    if (current.status() == Status.PLANNED || current.status() == Status.ROLLED_BACK) {
      throw new IllegalStateException("尚未迁移或已回滚作业不得重复回滚");
    }
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

  private static void requireBatchSize(int batchSize) {
    if (batchSize < 1 || batchSize > 1000) {
      throw new IllegalArgumentException("迁移批次大小必须在 1 到 1000 之间");
    }
  }

  private static void requireReason(String reason, String label) {
    if (reason == null || reason.length() < 2 || reason.length() > 1000 || reason.isBlank()) {
      throw new IllegalArgumentException(label + "长度必须在 2 到 1000 个字符之间");
    }
  }

  private static String revisionDigest(
      DomainPackageVersion source, DomainPackageVersion target, int batchSize) {
    return DomainPackageCompositionService.sha256(
        source.revision().value() + "|" + target.revision().value() + "|" + batchSize);
  }

  private static String canonicalizeScope(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, String>();
      map.forEach(
          (key, item) -> {
            if (!(key instanceof CharSequence)) {
              throw new IllegalArgumentException("迁移范围对象键必须是字符串");
            }
            sorted.put(key.toString(), canonicalizeScope(item));
          });
      return sorted.toString();
    }
    if (value instanceof Iterable<?> iterable) {
      var items = new ArrayList<String>();
      iterable.forEach(item -> items.add(canonicalizeScope(item)));
      return items.toString();
    }
    if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
      return value.getClass().getSimpleName() + ":" + value;
    }
    throw new IllegalArgumentException("迁移范围仅允许对象、数组、字符串、数字、布尔值或 null");
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
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(sourceVersionId, "sourceVersionId");
      Objects.requireNonNull(targetVersionId, "targetVersionId");
      Objects.requireNonNull(impact, "impact");
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
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(previewId, "迁移作业必须关联预览标识");
      Objects.requireNonNull(sourceVersionId, "sourceVersionId");
      Objects.requireNonNull(targetVersionId, "targetVersionId");
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(revision, "revision");
      Objects.requireNonNull(createdAt, "createdAt");
      requireBatchSize(batchSize);
      if (migrated < 0 || failed < 0) {
        throw new IllegalArgumentException("迁移成功和失败数量不能为负数");
      }
      failedInstances = List.copyOf(failedInstances);
    }
  }
}
