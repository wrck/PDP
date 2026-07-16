package com.pdp.mysql.identity;

import com.pdp.identity.domain.UserSession;
import com.pdp.identity.port.UserSessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlUserSessionRepository implements UserSessionRepository {

  private final UserSessionMapper mapper;

  public MysqlUserSessionRepository(UserSessionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<UserSession> findActiveByUserId(UUID userId) {
    return mapper.findActiveByUserId(userId).stream().map(UserSessionRow::toDomain).toList();
  }

  @Override
  public UserSession save(UserSession session) {
    UserSessionRow row = UserSessionRow.fromDomain(session);
    if (mapper.update(row) == 0) {
      mapper.insert(row);
    }
    return mapper.findById(session.id()).toDomain();
  }
}
