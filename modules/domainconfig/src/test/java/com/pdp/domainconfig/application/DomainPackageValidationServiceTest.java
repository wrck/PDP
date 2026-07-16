package com.pdp.domainconfig.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.domainconfig.domain.behavior.PermissionDefinition;
import com.pdp.domainconfig.domain.behavior.StateDefinition;
import com.pdp.domainconfig.domain.behavior.TopLifecycleState;
import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalog;
import com.pdp.domainconfig.domain.metamodel.CoreFieldDefinition;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.FieldDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainPackageValidationServiceTest {

  @Test
  void 应拒绝重复定义核心字段和无法映射的生命周期() {
    var catalog =
        new CoreFieldCatalog(
            List.of(
                new CoreFieldDefinition(
                    "project.name", "project", "项目名称", "TEXT", "pdp.project.name", Set.of("name"), true)));
    var manifest =
        new DomainPackageManifest(
            "customer.package",
            PackageLayer.WORKSPACE_CUSTOMER,
            null,
            List.of(
                new ObjectDefinition(
                    "project.custom",
                    ObjectDefinition.Kind.CORE_EXTENSION,
                    "project",
                    List.of(
                        new FieldDefinition(
                            "custom.name", "项目名称", "TEXT", "pdp.project.name", false, false)),
                    List.of(),
                    List.of(),
                    List.of(
                        new StateDefinition(
                            "unknown", "未知", null, true, false)),
                    List.of())),
            List.of(),
            List.of(new PermissionDefinition("project.read", "project.custom", Set.of("READ"), Set.of())),
            List.of(),
            List.of(),
            List.of());

    var report = new DomainPackageValidationService(catalog, binding -> List.of()).validate(manifest);

    assertThat(report.valid()).isFalse();
    assertThat(report.errors())
        .anyMatch(value -> value.contains("必须继承"))
        .anyMatch(value -> value.contains("核心字段"))
        .anyMatch(value -> value.contains("顶层生命周期"));
  }

  @Test
  void 声明式对象应通过核心字段引用和生命周期校验() {
    var catalog =
        new CoreFieldCatalog(
            List.of(
                new CoreFieldDefinition(
                    "project.name", "project", "项目名称", "TEXT", "pdp.project.name", Set.of("name"), true)));
    var manifest =
        new DomainPackageManifest(
            "network.cutover",
            PackageLayer.INDUSTRY,
            "pdp.standard",
            List.of(
                new ObjectDefinition(
                    "cutover.plan",
                    ObjectDefinition.Kind.NEW_OBJECT,
                    null,
                    List.of(
                        new FieldDefinition(
                            "cutover.window", "割接窗口", "DATETIME", "network.cutover.window", true, false)),
                    List.of(),
                    List.of(),
                    List.of(
                        new StateDefinition(
                            "draft", "草稿", TopLifecycleState.PLANNING, true, false)),
                    List.of())),
            List.of(),
            List.of(new PermissionDefinition("cutover.read", "cutover.plan", Set.of("READ"), Set.of())),
            List.of(),
            List.of(),
            List.of());

    assertThat(new DomainPackageValidationService(catalog, binding -> List.of()).validate(manifest).valid())
        .isTrue();
  }
}
