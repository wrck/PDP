package com.pdp.identity.infrastructure.oidc;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

public final class OidcAuthenticationException extends PdpException {
  public OidcAuthenticationException(String detail) {
    super(ErrorCode.INVALID_REQUEST, detail);
  }
}
