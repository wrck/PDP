package com.pdp.identity.application;

public record AuthorizationDecision(
    boolean allowed, String reasonCode, String reason, long authorizationVersion) {
  public static AuthorizationDecision allow(long version) {
    return new AuthorizationDecision(true, "ALLOWED", "授权通过", version);
  }

  public static AuthorizationDecision deny(String code, String reason, long version) {
    return new AuthorizationDecision(false, code, reason, version);
  }
}
