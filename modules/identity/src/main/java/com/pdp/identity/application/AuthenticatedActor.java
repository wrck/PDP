package com.pdp.identity.application;

import java.util.UUID;

/** 安全适配器向 API 暴露的平台操作者身份。 */
public record AuthenticatedActor(UUID userId, long authorizationVersion) {
  public AuthenticatedActor {
    if (userId == null || authorizationVersion < 0) {
      throw new IllegalArgumentException("认证操作者标识或授权版本无效");
    }
  }
}
