package com.pdp.identity.port;

import com.pdp.identity.domain.UserSession;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository {
  List<UserSession> findActiveByUserId(UUID userId);

  UserSession save(UserSession session);
}
