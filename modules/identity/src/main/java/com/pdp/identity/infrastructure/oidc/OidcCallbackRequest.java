package com.pdp.identity.infrastructure.oidc;

public record OidcCallbackRequest(String authorizationCode, String state) {
  public OidcCallbackRequest {
    if (authorizationCode == null
        || authorizationCode.isBlank()
        || state == null
        || state.isBlank()) {
      throw new IllegalArgumentException("OIDC code 和 state 不能为空");
    }
  }
}
