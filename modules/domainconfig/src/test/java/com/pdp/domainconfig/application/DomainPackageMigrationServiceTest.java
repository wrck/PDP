package com.pdp.domainconfig.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.domainconfig.port.DomainPackageMigrationRepository;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import com.pdp.shared.operation.OperationConfirmation;
import com.pdp.shared.operation.OperationConfirmationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainPackageMigrationServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void 升级应先预览再分批执行并隔离失败实例且支持回滚() {
    var store = new Store();
    UUID packageId = UUID.randomUUID();
    DomainPackageVersion source = published(packageId, "1.0.0");
    DomainPackageVersion target = published(packageId, "1.1.0");
    store.save(source);
    store.save(target);
    var confirmations = new Confirmations();
    var service =
        new DomainPackageMigrationService(
            store,
            (versionId, scope) -> 250,
            store,
            confirmations,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var preview = service.preview(source.id(), target.id(), Map.of("workspace", "all"), 100);
    var job = service.start(preview.id(), preview.impact().confirmationToken(), 100, "升级字段模型");
    UUID failedInstance = UUID.randomUUID();
    job = service.recordBatch(job.id(), 99, List.of(failedInstance));
    job = service.rollback(job.id(), "失败实例需要人工修复");

    assertThat(preview.batches()).isEqualTo(3);
    assertThat(job.failedInstances()).containsExactly(failedInstance);
    assertThat(job.status()).isEqualTo(DomainPackageMigrationService.Status.ROLLED_BACK);
  }

  private static DomainPackageVersion published(UUID packageId, String version) {
    UUID creator = UUID.randomUUID();
    return DomainPackageVersion.draft(
            packageId,
            version,
            new DomainPackageManifest(
                "pdp.standard",
                PackageLayer.PLATFORM_STANDARD,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            version,
            creator,
            NOW)
        .validate()
        .submitReview()
        .approve(UUID.randomUUID())
        .publish(UUID.randomUUID());
  }

  static final class Confirmations implements OperationConfirmationPort {
    private OperationConfirmation issued;

    public String issue(OperationConfirmation confirmation) {
      issued = confirmation;
      return "confirmed";
    }

    public OperationConfirmation verify(String token, OperationConfirmation expected) {
      assertThat(token).isEqualTo("confirmed");
      assertThat(expected).isEqualTo(issued);
      return issued;
    }
  }

  static final class Store
      implements DomainPackageVersionRepository, DomainPackageMigrationRepository {
    final Map<UUID, DomainPackageVersion> versions = new HashMap<>();
    final Map<UUID, DomainPackageMigrationService.MigrationPreview> previews = new HashMap<>();
    final Map<UUID, DomainPackageMigrationService.MigrationJob> jobs = new HashMap<>();

    public Optional<DomainPackageVersion> findVersionById(UUID id) { return Optional.ofNullable(versions.get(id)); }
    public Optional<DomainPackageVersion> findByPackageAndSemanticVersion(UUID packageId, String semanticVersion) { return Optional.empty(); }
    public List<DomainPackageVersion> findVersionsByPackageId(UUID packageId) { return List.of(); }
    public DomainPackageVersion save(DomainPackageVersion value) { versions.put(value.id(), value); return value; }
    public DomainPackageMigrationService.MigrationPreview savePreview(DomainPackageMigrationService.MigrationPreview value) { previews.put(value.id(), value); return value; }
    public Optional<DomainPackageMigrationService.MigrationPreview> findPreview(UUID id) { return Optional.ofNullable(previews.get(id)); }
    public DomainPackageMigrationService.MigrationJob saveJob(DomainPackageMigrationService.MigrationJob value) { jobs.put(value.id(), value); return value; }
    public Optional<DomainPackageMigrationService.MigrationJob> findJob(UUID id) { return Optional.ofNullable(jobs.get(id)); }
  }
}
