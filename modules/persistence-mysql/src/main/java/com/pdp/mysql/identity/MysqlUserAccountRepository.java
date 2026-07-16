package com.pdp.mysql.identity;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.port.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlUserAccountRepository implements UserAccountRepository {

  private final UserAccountMapper mapper;

  public MysqlUserAccountRepository(UserAccountMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(UserAccountRow::toDomain);
  }

  @Override
  public Optional<UserAccount> findByExternalSubject(String subject) {
    return Optional.ofNullable(mapper.findByExternalSubject(subject))
        .map(UserAccountRow::toDomain);
  }

  @Override
  public UserAccount save(UserAccount account) {
    UserAccountRow row = UserAccountRow.fromDomain(account);
    if (mapper.update(row) == 0) {
      mapper.insert(row);
    }
    return mapper.findById(account.id()).toDomain();
  }
}
