package com.pdp.identity.application;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;
import java.util.Map;

public final class AuthorizationDeniedException extends PdpException {
  public AuthorizationDeniedException(AuthorizationDecision decision) {
    super(
        ErrorCode.ACCESS_DENIED,
        "无权访问当前工作空间或资源",
        null,
        Map.of("reasonCode", decision.reasonCode()));
  }
}
