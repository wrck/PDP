package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationJob;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationPreview;
import com.pdp.persistence.type.JsonDocument;
import java.time.Instant;
import java.util.UUID;

public record DomainPackageMigrationRow(
    UUID id,
    UUID sourceVersionId,
    UUID targetVersionId,
    UUID previewId,
    String status,
    int batchSize,
    long migratedCount,
    long failedCount,
    JsonDocument failureDetails,
    JsonDocument rollbackPlan,
    long revision,
    Instant createdAt) {

  static DomainPackageMigrationRow fromPreview(MigrationPreview preview) {
    return new DomainPackageMigrationRow(
        preview.id(),
        preview.sourceVersionId(),
        preview.targetVersionId(),
        preview.id(),
        "PREVIEW",
        preview.batches(),
        preview.affectedInstances(),
        preview.conflicts().size(),
        DomainConfigJsonCodec.write(preview.conflicts()),
        DomainConfigJsonCodec.write(preview),
        0,
        preview.impact().expiresAt().minusSeconds(900));
  }

  static DomainPackageMigrationRow fromJob(MigrationJob job) {
    return new DomainPackageMigrationRow(
        job.id(),
        job.sourceVersionId(),
        job.targetVersionId(),
        job.previewId(),
        job.status().name(),
        job.batchSize(),
        job.migrated(),
        job.failed(),
        DomainConfigJsonCodec.uuidList(job.failedInstances()),
        DomainConfigJsonCodec.write(job),
        job.revision().value(),
        job.createdAt());
  }

  MigrationPreview toPreview() {
    return DomainConfigJsonCodec.read(rollbackPlan, MigrationPreview.class);
  }

  MigrationJob toJob() {
    return DomainConfigJsonCodec.read(rollbackPlan, MigrationJob.class);
  }
}
