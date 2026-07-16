package com.pdp.shared.operation;

public interface OperationConfirmationPort {
  String issue(OperationConfirmation confirmation);

  OperationConfirmation verify(String token, OperationConfirmation expected);
}
