package com.pdp.shared.operation;

/** P1 高风险操作稳定标识。 */
public final class HighRiskOperationType {
  public static final String DOMAIN_PACKAGE_RELEASE = "DOMAIN_PACKAGE_RELEASE";
  public static final String DOMAIN_PACKAGE_MIGRATION = "DOMAIN_PACKAGE_MIGRATION";
  public static final String PROJECT_ROLLBACK = "PROJECT_ROLLBACK";
  public static final String BASELINE_REPLACEMENT = "BASELINE_REPLACEMENT";
  public static final String MANUAL_PROGRESS_ADJUSTMENT = "MANUAL_PROGRESS_ADJUSTMENT";
  public static final String DELIVERABLE_RELEASE = "DELIVERABLE_RELEASE";
  public static final String APPROVAL_FINALIZATION = "APPROVAL_FINALIZATION";
  public static final String DATA_DISPOSITION = "DATA_DISPOSITION";
  public static final String LEGACY_CUTOVER = "LEGACY_CUTOVER";
  public static final String DATABASE_SWITCH = "DATABASE_SWITCH";

  private HighRiskOperationType() {}
}
