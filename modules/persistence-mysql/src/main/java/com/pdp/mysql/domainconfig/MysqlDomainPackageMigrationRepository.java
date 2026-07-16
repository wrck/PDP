package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationJob;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationPreview;
import com.pdp.domainconfig.port.DomainPackageMigrationRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlDomainPackageMigrationRepository
    implements DomainPackageMigrationRepository {

  private final DomainPackageMigrationMapper mapper;

  public MysqlDomainPackageMigrationRepository(DomainPackageMigrationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public MigrationPreview savePreview(MigrationPreview preview) {
    DomainPackageMigrationRow row = DomainPackageMigrationRow.fromPreview(preview);
    if (mapper.updatePreview(row) == 0) {
      mapper.insert(row);
    }
    return mapper.findPreview(preview.id()).toPreview();
  }

  @Override
  public Optional<MigrationPreview> findPreview(UUID previewId) {
    return Optional.ofNullable(mapper.findPreview(previewId))
        .map(DomainPackageMigrationRow::toPreview);
  }

  @Override
  public MigrationJob saveJob(MigrationJob job) {
    DomainPackageMigrationRow preview = mapper.findPreview(job.previewId());
    if (preview == null) {
      throw new IllegalArgumentException("迁移作业关联的预览不存在: " + job.previewId());
    }
    if (!preview.sourceVersionId().equals(job.sourceVersionId())
        || !preview.targetVersionId().equals(job.targetVersionId())) {
      throw new IllegalArgumentException("迁移作业与预览的源/目标版本不一致");
    }
    DomainPackageMigrationRow row = DomainPackageMigrationRow.fromJob(job);
    if (mapper.updateJob(row) == 0) {
      DomainPackageMigrationRow current = mapper.findJob(row.id());
      if (current != null) {
        throw new ConcurrencyConflictException(
            new Revision(Math.max(0, row.revision() - 1)), new Revision(current.revision()));
      }
      mapper.insert(row);
    }
    return mapper.findJob(job.id()).toJob();
  }

  @Override
  public Optional<MigrationJob> findJob(UUID jobId) {
    return Optional.ofNullable(mapper.findJob(jobId)).map(DomainPackageMigrationRow::toJob);
  }
}
