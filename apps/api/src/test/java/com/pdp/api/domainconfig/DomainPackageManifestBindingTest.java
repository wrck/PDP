package com.pdp.api.domainconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DomainPackageManifestBindingTest {

  @Test
  void 正式示例包应能绑定为后端领域模型() throws Exception {
    Path fixture =
        Path.of("../../tests/fixtures/domain-package/network-cutover-package.json").normalize();
    DomainPackageManifest manifest =
        new ObjectMapper().readValue(Files.readString(fixture), DomainPackageManifest.class);

    assertThat(manifest.stableKey()).isEqualTo("network.cutover");
    assertThat(manifest.parentPackageKey()).isEqualTo("pdp.standard-delivery");
    assertThat(manifest.objects()).hasSize(1);
    assertThat(manifest.workflowBindings()).hasSize(1);
    assertThat(manifest.migrations()).hasSize(1);
  }
}
