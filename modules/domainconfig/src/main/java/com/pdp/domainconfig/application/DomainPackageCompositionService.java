package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.DomainPackageSnapshot;
import com.pdp.domainconfig.domain.metamodel.FieldDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class DomainPackageCompositionService {

  public CompositionResult compose(DomainPackageManifest... manifests) {
    if (manifests.length == 0 || manifests.length > 3) {
      throw new IllegalArgumentException("领域包组合必须包含一至三层");
    }
    validateLayerOrder(manifests);
    var objects = new LinkedHashMap<String, ObjectDefinition>();
    var fieldTypes = new LinkedHashMap<String, String>();
    var conflicts = new ArrayList<String>();
    var differences = new ArrayList<String>();
    var layers = new ArrayList<String>();
    for (var manifest : manifests) {
      layers.add(manifest.stableKey());
      for (var object : manifest.objects()) {
        ObjectDefinition previous = objects.put(object.stableKey(), object);
        if (previous != null) {
          differences.add("覆盖对象: " + object.stableKey());
        } else {
          differences.add("新增对象: " + object.stableKey());
        }
        for (FieldDefinition field : object.fields()) {
          String previousType = fieldTypes.put(field.stableKey(), field.dataType());
          if (previousType != null && !previousType.equals(field.dataType())) {
            conflicts.add(
                "字段类型冲突 "
                    + field.stableKey()
                    + ": "
                    + previousType
                    + " -> "
                    + field.dataType());
          }
        }
      }
    }
    String hash = sha256(layers + "|" + objects);
    var snapshot =
        new DomainPackageSnapshot(UUID.randomUUID(), layers, Map.copyOf(objects), hash);
    return new CompositionResult(snapshot, differences, conflicts);
  }

  private static void validateLayerOrder(DomainPackageManifest[] manifests) {
    PackageLayer[] expected = {
      PackageLayer.PLATFORM_STANDARD, PackageLayer.INDUSTRY, PackageLayer.WORKSPACE_CUSTOMER
    };
    for (int index = 0; index < manifests.length; index++) {
      if (manifests[index].layer() != expected[index]) {
        throw new IllegalArgumentException("领域包必须按平台标准、行业、工作空间客户三层组合");
      }
      if (index > 0
          && !manifests[index - 1].stableKey().equals(manifests[index].parentPackageKey())) {
        throw new IllegalArgumentException("领域包继承链不连续");
      }
    }
  }

  static String sha256(Object value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(value.toString().getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public record CompositionResult(
      DomainPackageSnapshot snapshot, List<String> differences, List<String> conflicts) {
    public CompositionResult {
      differences = List.copyOf(differences);
      conflicts = List.copyOf(conflicts);
    }
  }
}
