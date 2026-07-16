package com.pdp.shared.operation;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

public final class OperationConfirmationException extends PdpException {
  public OperationConfirmationException(String detail) {
    super(ErrorCode.OPERATION_CONFIRMATION_INVALID, detail);
  }
}
