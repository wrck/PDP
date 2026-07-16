package com.pdp.identity.port;

import com.pdp.identity.application.AuthorizationRevoked;

@FunctionalInterface
public interface AuthorizationRevocationPublisher {
  void publish(AuthorizationRevoked event);
}
