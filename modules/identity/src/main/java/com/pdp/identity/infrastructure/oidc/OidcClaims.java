package com.pdp.identity.infrastructure.oidc;

public record OidcClaims(
    String issuer,
    String subject,
    String identityProviderSessionId,
    String displayName,
    String email,
    boolean emailVerified) {
  public OidcClaims {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("OIDC issuer 和 subject 不能为空");
    }
    if (displayName == null || displayName.isBlank() || email == null || email.isBlank()) {
      throw new IllegalArgumentException("OIDC 显示名称和邮箱不能为空");
    }
  }
}
