package com.pdp.domainconfig.port;

import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationJob;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationPreview;
import java.util.Optional;
import java.util.UUID;

public interface DomainPackageMigrationRepository {
  MigrationPreview savePreview(MigrationPreview preview);

  Optional<MigrationPreview> findPreview(UUID previewId);

  MigrationJob saveJob(MigrationJob job);

  Optional<MigrationJob> findJob(UUID jobId);
}
