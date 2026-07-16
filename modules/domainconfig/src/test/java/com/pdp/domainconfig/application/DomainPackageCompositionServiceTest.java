package com.pdp.domainconfig.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.FieldDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainPackageCompositionServiceTest {

  @Test
  void 三层继承应生成差异快照并报告字段冲突() {
    var platform = manifest("pdp.standard", PackageLayer.PLATFORM_STANDARD, null, "site.address", "TEXT");
    var industry = manifest("network.delivery", PackageLayer.INDUSTRY, "pdp.standard", "device.model", "TEXT");
    var customer =
        manifest(
            "customer.network",
            PackageLayer.WORKSPACE_CUSTOMER,
            "network.delivery",
            "device.model",
            "INTEGER");

    UUID packageVersionId = UUID.randomUUID();
    var result =
        new DomainPackageCompositionService()
            .compose(packageVersionId, platform, industry, customer);

    assertThat(result.snapshot().packageVersionId()).isEqualTo(packageVersionId);
    assertThat(result.snapshot().layers()).containsExactly(
        "pdp.standard", "network.delivery", "customer.network");
    assertThat(result.conflicts()).anyMatch(value -> value.contains("device.model"));
    assertThat(result.differences()).isNotEmpty();
  }

  private static DomainPackageManifest manifest(
      String key, PackageLayer layer, String parent, String field, String type) {
    return new DomainPackageManifest(
        key,
        layer,
        parent,
        List.of(
            new ObjectDefinition(
                "delivery.site",
                ObjectDefinition.Kind.NEW_OBJECT,
                null,
                List.of(new FieldDefinition(field, field, type, key + "." + field, false, false)),
                List.of(),
                List.of(),
                List.of(),
                List.of())),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
