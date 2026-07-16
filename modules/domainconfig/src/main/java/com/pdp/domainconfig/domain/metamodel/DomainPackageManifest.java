package com.pdp.domainconfig.domain.metamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pdp.domainconfig.domain.behavior.GovernedExtension;
import com.pdp.domainconfig.domain.behavior.MigrationDefinition;
import com.pdp.domainconfig.domain.behavior.PermissionDefinition;
import com.pdp.domainconfig.domain.behavior.OverrideDefinition;
import com.pdp.domainconfig.domain.behavior.RuleDefinition;
import com.pdp.domainconfig.domain.behavior.WorkflowBinding;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainPackageManifest(
    String schemaVersion,
    String stableKey,
    String name,
    String description,
    PackageLayer layer,
    String version,
    @JsonProperty("extends") PackageReference extendsPackage,
    List<ObjectDefinition> objects,
    List<PageDefinition> pages,
    List<ViewDefinition> views,
    List<RuleDefinition> rules,
    List<PermissionDefinition> permissions,
    List<GovernedExtension> extensions,
    List<WorkflowBinding> workflowBindings,
    List<OverrideDefinition> overrides,
    List<MigrationDefinition> migrations) {
  public record PackageReference(String packageKey, String versionRange) {}

  public DomainPackageManifest {
    objects = List.copyOf(objects == null ? List.of() : objects);
    pages = List.copyOf(pages == null ? List.of() : pages);
    views = List.copyOf(views == null ? List.of() : views);
    rules = List.copyOf(rules == null ? List.of() : rules);
    permissions = List.copyOf(permissions == null ? List.of() : permissions);
    extensions = List.copyOf(extensions == null ? List.of() : extensions);
    workflowBindings = List.copyOf(workflowBindings == null ? List.of() : workflowBindings);
    overrides = List.copyOf(overrides == null ? List.of() : overrides);
    migrations = List.copyOf(migrations == null ? List.of() : migrations);
  }

  public DomainPackageManifest(
      String stableKey,
      PackageLayer layer,
      String parentPackageKey,
      List<ObjectDefinition> objects,
      List<RuleDefinition> rules,
      List<PermissionDefinition> permissions,
      List<GovernedExtension> extensions,
      List<WorkflowBinding> workflowBindings,
      List<MigrationDefinition> migrations) {
    this(
        "1.1",
        stableKey,
        stableKey,
        null,
        layer,
        "0.0.0",
        parentPackageKey == null ? null : new PackageReference(parentPackageKey, "*"),
        objects,
        List.of(),
        List.of(),
        rules,
        permissions,
        extensions,
        workflowBindings,
        List.of(),
        migrations);
  }

  public String parentPackageKey() {
    return extendsPackage == null ? null : extendsPackage.packageKey();
  }
}
