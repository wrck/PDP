package com.pdp.integration.event;

@FunctionalInterface
public interface DomainEventHandler {

  void handle(DomainEventEnvelope event) throws Exception;
}
