package com.pdp.domainconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalog;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.domainconfig.port.DomainPackageEventPublisher;
import com.pdp.domainconfig.port.DomainPackageRepository;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainPackageLifecycleServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void 创建者不得审核且批准发布后版本必须冻结() {
    var store = new Store();
    UUID creator = UUID.randomUUID();
    UUID reviewer = UUID.randomUUID();
    var service =
        new DomainPackageLifecycleService(
            store,
            store,
            new DomainPackageValidationService(new CoreFieldCatalog(List.of()), binding -> List.of()),
            DomainPackageEventPublisher.noop(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    UUID workspaceId = UUID.randomUUID();
    DomainPackage parent =
        service.createPackage(
            workspaceId, "pdp.standard", "平台标准", PackageLayer.PLATFORM_STANDARD, null);
    DomainPackage domainPackage =
        service.createPackage(
            workspaceId,
            "network.cutover",
            "网络割接",
            PackageLayer.INDUSTRY,
            parent.id());
    DomainPackageVersion version =
        service.createVersion(
            domainPackage.id(),
            "1.0.0",
            new DomainPackageManifest(
                "network.cutover",
                PackageLayer.INDUSTRY,
                "pdp.standard",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            creator);

    version = service.validate(domainPackage.id(), version.id(), version.revision());
    version = service.submitReview(domainPackage.id(), version.id(), version.revision(), creator);
    DomainPackageVersion reviewPending = version;
    assertThatThrownBy(
            () ->
                service.review(
                    domainPackage.id(),
                    reviewPending.id(),
                    reviewPending.revision(),
                    creator,
                    true,
                    "自审"))
        .isInstanceOf(IllegalStateException.class);

    version =
        service.review(
            domainPackage.id(), version.id(), version.revision(), reviewer, true, "审核通过");
    version =
        service.publish(
            domainPackage.id(), version.id(), version.revision(), UUID.randomUUID(), "批准发布");

    assertThat(version.status()).isEqualTo(DomainPackageVersion.Status.PUBLISHED);
    assertThat(version.frozen()).isTrue();
  }

  static final class Store implements DomainPackageRepository, DomainPackageVersionRepository {
    final Map<UUID, DomainPackage> packages = new HashMap<>();
    final Map<UUID, DomainPackageVersion> versions = new HashMap<>();

    public Optional<DomainPackage> findById(UUID id) { return Optional.ofNullable(packages.get(id)); }
    public Optional<DomainPackage> findByStableKey(UUID workspaceId, String stableKey) {
      return packages.values().stream().filter(value -> value.workspaceId().equals(workspaceId) && value.stableKey().equals(stableKey)).findFirst();
    }
    public DomainPackage save(DomainPackage value) { packages.put(value.id(), value); return value; }
    public List<DomainPackage> findByWorkspace(UUID workspaceId) {
      return packages.values().stream().filter(value -> value.workspaceId().equals(workspaceId)).toList();
    }
    public Optional<DomainPackageVersion> findVersionById(UUID id) { return Optional.ofNullable(versions.get(id)); }
    public Optional<DomainPackageVersion> findByPackageAndSemanticVersion(UUID packageId, String semanticVersion) {
      return versions.values().stream().filter(value -> value.packageId().equals(packageId) && value.semanticVersion().equals(semanticVersion)).findFirst();
    }
    public List<DomainPackageVersion> findVersionsByPackageId(UUID packageId) {
      return versions.values().stream().filter(value -> value.packageId().equals(packageId)).toList();
    }
    public DomainPackageVersion save(DomainPackageVersion value) { versions.put(value.id(), value); return value; }
  }
}
