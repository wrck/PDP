package com.pdp.shared.context;

import java.util.Objects;
import java.util.UUID;

public record ActorId(UUID value) {
  public ActorId {
    Objects.requireNonNull(value, "actorId 不能为空");
  }
}
