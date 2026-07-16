package com.pdp.shared.operation;

@FunctionalInterface
public interface CompensationPort {
  CompensationResult compensate(CompensationRequest request);
}
