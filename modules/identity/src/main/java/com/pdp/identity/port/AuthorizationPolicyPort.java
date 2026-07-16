package com.pdp.identity.port;

import com.pdp.identity.application.AuthorizationRequest;

@FunctionalInterface
public interface AuthorizationPolicyPort {
  boolean isAllowed(AuthorizationRequest request);
}
