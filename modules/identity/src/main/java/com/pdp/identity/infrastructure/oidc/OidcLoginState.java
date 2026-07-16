package com.pdp.identity.infrastructure.oidc;

public record OidcLoginState(String state, String nonce, String redirectUri) {
  public OidcLoginState {
    if (state == null
        || state.isBlank()
        || nonce == null
        || nonce.isBlank()
        || redirectUri == null
        || redirectUri.isBlank()) {
      throw new IllegalArgumentException("OIDC 登录状态不能为空");
    }
  }
}
