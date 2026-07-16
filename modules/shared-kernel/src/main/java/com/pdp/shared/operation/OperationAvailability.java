package com.pdp.shared.operation;

public record OperationAvailability(boolean enabled, String reasonCode, String reason) {
  public OperationAvailability {
    if (reasonCode == null || reasonCode.isBlank() || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("能力判定必须包含稳定原因码和说明");
    }
  }

  public static OperationAvailability certified() {
    return new OperationAvailability(true, "CERTIFIED", "组合已认证");
  }

  public static OperationAvailability disabled(String code, String reason) {
    return new OperationAvailability(false, code, reason);
  }
}
