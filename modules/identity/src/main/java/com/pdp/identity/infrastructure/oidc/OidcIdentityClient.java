package com.pdp.identity.infrastructure.oidc;

@FunctionalInterface
public interface OidcIdentityClient {
  OidcClaims exchangeAndVerify(OidcCallbackRequest request);

  default String authorizationUri(OidcLoginState state) {
    throw new UnsupportedOperationException("OIDC 客户端未实现登录地址生成");
  }
}
