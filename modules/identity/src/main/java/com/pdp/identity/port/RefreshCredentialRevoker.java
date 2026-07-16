package com.pdp.identity.port;

@FunctionalInterface
public interface RefreshCredentialRevoker {
  void revoke(String credentialReference);
}
