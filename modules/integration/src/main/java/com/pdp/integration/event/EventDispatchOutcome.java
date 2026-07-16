package com.pdp.integration.event;

import com.pdp.integration.event.EventDeliveryStore.Decision;
import com.pdp.integration.event.EventDeliveryStore.Delivery;
import java.util.Optional;

public record EventDispatchOutcome(Decision decision, Optional<Delivery> delivery) {

  public EventDispatchOutcome {
    delivery = delivery == null ? Optional.empty() : delivery;
  }
}
