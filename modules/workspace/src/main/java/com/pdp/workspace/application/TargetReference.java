package com.pdp.workspace.application;

import java.util.UUID;

public record TargetReference(String objectType, UUID objectId) {
  public TargetReference {
    if (objectType == null || objectType.isBlank() || objectId == null) {
      throw new IllegalArgumentException("目标引用不能为空");
    }
  }
}
