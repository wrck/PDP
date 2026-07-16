package com.pdp.shared.operation;

import java.util.Locale;
import java.util.Set;

/** P1 数据库切换认证策略；产品与版本判定顺序固定，确保禁用原因稳定。 */
public final class DatabaseSwitchCertificationPolicy {
  private final Set<String> products;
  private final String certifiedMajorMinor;

  private DatabaseSwitchCertificationPolicy(Set<String> products, String certifiedMajorMinor) {
    this.products = Set.copyOf(products);
    this.certifiedMajorMinor = certifiedMajorMinor;
  }

  public static DatabaseSwitchCertificationPolicy p1Mysql84() {
    return new DatabaseSwitchCertificationPolicy(Set.of("MYSQL"), "8.4");
  }

  public OperationAvailability evaluate(
      String sourceProduct,
      String sourceVersion,
      String targetProduct,
      String targetVersion) {
    String source = normalize(sourceProduct);
    String target = normalize(targetProduct);
    if (!products.contains(source)) {
      return OperationAvailability.disabled(
          "SOURCE_PRODUCT_NOT_CERTIFIED", "源数据库产品未通过 P1 切换认证");
    }
    if (!products.contains(target)) {
      return OperationAvailability.disabled(
          "TARGET_PRODUCT_NOT_CERTIFIED", "目标数据库产品未通过 P1 切换认证");
    }
    if (!isCertifiedVersion(sourceVersion)) {
      return OperationAvailability.disabled(
          "SOURCE_VERSION_NOT_CERTIFIED", "源数据库版本未通过 P1 切换认证");
    }
    if (!isCertifiedVersion(targetVersion)) {
      return OperationAvailability.disabled(
          "TARGET_VERSION_NOT_CERTIFIED", "目标数据库版本未通过 P1 切换认证");
    }
    if (!source.equals(target)) {
      return OperationAvailability.disabled(
          "DATABASE_SWITCH_COMBINATION_NOT_CERTIFIED", "源目标数据库产品组合未认证");
    }
    return OperationAvailability.certified();
  }

  private boolean isCertifiedVersion(String version) {
    return version != null
        && (version.equals(certifiedMajorMinor) || version.startsWith(certifiedMajorMinor + "."));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
