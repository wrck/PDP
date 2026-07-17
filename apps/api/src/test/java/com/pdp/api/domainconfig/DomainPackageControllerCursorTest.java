package com.pdp.api.domainconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdp.domainconfig.application.DomainPackageLifecycleService;
import com.pdp.domainconfig.application.DomainPackageMigrationService;
import com.pdp.domainconfig.application.DomainPackageValidationService;
import com.pdp.api.domainconfig.DomainPackageController.DomainPackageResponse;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
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

class DomainPackageControllerCursorTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);
  private static final UUID WORKSPACE_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
  private static final UUID WORKSPACE_B = UUID.fromString("01900000-0000-7000-8000-000000000002");

  @Test
  void 签名游标必须绑定工作空间和筛选条件并拒绝篡改() {
    DomainPackageLifecycleService lifecycle = mock(DomainPackageLifecycleService.class);
    when(lifecycle.list(WORKSPACE_A))
        .thenReturn(
            List.of(
                domainPackage(1, "alpha.standard", PackageLayer.PLATFORM_STANDARD),
                domainPackage(2, "network.cutover", PackageLayer.INDUSTRY),
                domainPackage(3, "workspace.custom", PackageLayer.WORKSPACE_CUSTOMER)));
    when(lifecycle.list(WORKSPACE_B)).thenReturn(List.of());
    var controller =
        new DomainPackageController(
            lifecycle,
            mock(DomainPackageValidationService.class),
            mock(DomainPackageMigrationService.class),
            new SignedKeysetCursorCodec(
                "k1",
                Map.of(
                    "k1",
                    "a-32-byte-domain-package-cursor-key"
                        .getBytes(StandardCharsets.UTF_8)),
                Duration.ofHours(1),
                CLOCK),
            CLOCK);

    var first = controller.list(WORKSPACE_A, null, null, 2);
    assertThat(first.items()).extracting(DomainPackageResponse::stableKey)
        .containsExactly("alpha.standard", "network.cutover");
    assertThat(first.nextCursor()).isNotBlank();
    assertThat(controller.list(WORKSPACE_A, null, first.nextCursor(), 2).items())
        .extracting(DomainPackageResponse::stableKey)
        .containsExactly("workspace.custom");

    String tampered =
        first.nextCursor().substring(0, first.nextCursor().length() - 1)
            + (first.nextCursor().endsWith("A") ? "B" : "A");
    assertThatThrownBy(() -> controller.list(WORKSPACE_A, null, tampered, 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("游标");
    assertThatThrownBy(() -> controller.list(WORKSPACE_B, null, first.nextCursor(), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("游标");
    assertThatThrownBy(
            () ->
                controller.list(
                    WORKSPACE_A,
                    PackageLayer.INDUSTRY,
                    first.nextCursor(),
                    2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("游标");
  }

  private static DomainPackage domainPackage(long suffix, String key, PackageLayer layer) {
    return new DomainPackage(
        new UUID(0, suffix),
        WORKSPACE_A,
        key,
        key,
        layer,
        layer == PackageLayer.PLATFORM_STANDARD ? null : new UUID(0, 99),
        DomainPackage.Status.ACTIVE,
        new Revision(0));
  }
}
