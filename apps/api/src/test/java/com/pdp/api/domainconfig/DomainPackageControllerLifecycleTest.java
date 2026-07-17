package com.pdp.api.domainconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdp.domainconfig.application.DomainPackageLifecycleService;
import com.pdp.domainconfig.application.DomainPackageMigrationService;
import com.pdp.domainconfig.application.DomainPackageValidationService;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.shared.concurrency.EntityTag;
import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.page.SignedKeysetCursorCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class DomainPackageControllerLifecycleTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);
  private static final UUID WORKSPACE_ID =
      UUID.fromString("01900000-0000-7000-8000-000000000001");
  private static final UUID PACKAGE_ID =
      UUID.fromString("01900000-0000-7000-8000-000000000002");
  private static final UUID VERSION_ID =
      UUID.fromString("01900000-0000-7000-8000-000000000003");
  private static final UUID CREATOR_ID =
      UUID.fromString("01900000-0000-7000-8000-000000000004");
  private static final UUID REVIEWER_ID =
      UUID.fromString("01900000-0000-7000-8000-000000000005");

  @Test
  void 审核接口只审核待审版本且不代替创建者提交审核() {
    DomainPackageLifecycleService lifecycle = mock(DomainPackageLifecycleService.class);
    when(lifecycle.list(WORKSPACE_ID)).thenReturn(List.of(domainPackage()));
    DomainPackageVersion approved = version(DomainPackageVersion.Status.APPROVED, 3, REVIEWER_ID);
    when(lifecycle.review(
            PACKAGE_ID, VERSION_ID, new Revision(2), REVIEWER_ID, true, "审核通过"))
        .thenReturn(approved);
    var controller = controller(lifecycle);

    var response =
        controller.review(
            WORKSPACE_ID,
            PACKAGE_ID,
            VERSION_ID,
            EntityTag.from(new Revision(2)).value(),
            new UsernamePasswordAuthenticationToken(REVIEWER_ID.toString(), "n/a"),
            new DomainPackageController.ReviewRequest(
                DomainPackageController.ReviewDecision.APPROVE, "审核通过"));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(DomainPackageVersion.Status.APPROVED);
    assertThat(response.getBody().revision()).isEqualTo(3);
    verify(lifecycle, never()).submitReview(any(), any(), any(), any());
  }

  private static DomainPackageController controller(DomainPackageLifecycleService lifecycle) {
    return new DomainPackageController(
        lifecycle,
        mock(DomainPackageValidationService.class),
        mock(DomainPackageMigrationService.class),
        new SignedKeysetCursorCodec(
            "k1",
            Map.of(
                "k1", "a-32-byte-domain-package-cursor-key".getBytes(StandardCharsets.UTF_8)),
            Duration.ofHours(1),
            CLOCK),
        CLOCK);
  }

  private static DomainPackage domainPackage() {
    return new DomainPackage(
        PACKAGE_ID,
        WORKSPACE_ID,
        "network.cutover",
        "网络设备割接",
        PackageLayer.PLATFORM_STANDARD,
        null,
        DomainPackage.Status.ACTIVE,
        new Revision(1));
  }

  private static DomainPackageVersion version(
      DomainPackageVersion.Status status, long revision, UUID reviewedBy) {
    return new DomainPackageVersion(
        VERSION_ID,
        PACKAGE_ID,
        "1.0.0",
        new DomainPackageManifest(
            "network.cutover",
            PackageLayer.PLATFORM_STANDARD,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()),
        "sha256:test",
        status,
        false,
        CREATOR_ID,
        reviewedBy,
        null,
        new Revision(revision),
        CLOCK.instant());
  }
}
