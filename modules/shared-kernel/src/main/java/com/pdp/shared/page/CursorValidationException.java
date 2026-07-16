package com.pdp.shared.page;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

public final class CursorValidationException extends PdpException {
  public CursorValidationException(String message) {
    super(ErrorCode.CURSOR_INVALID, message);
  }
}
