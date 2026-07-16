package com.pdp.integration.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventInfrastructureConfiguration {

  @Bean
  TransactionalOutboxPublisher transactionalOutboxPublisher(
      ApplicationEventPublisher eventPublisher) {
    return new TransactionalOutboxPublisher(eventPublisher);
  }
}
