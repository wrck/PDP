package com.pdp.shared.operation;

/** 由各业务模块实现影响范围计算，共享内核只定义契约。 */
@FunctionalInterface
public interface OperationPreviewPort {
  OperationImpactPreview preview(OperationPreviewRequest request);
}
