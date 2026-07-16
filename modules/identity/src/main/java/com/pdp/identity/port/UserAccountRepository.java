package com.pdp.identity.port;

import com.pdp.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
  Optional<UserAccount> findById(UUID id);

  Optional<UserAccount> findByExternalSubject(String subject);

  UserAccount save(UserAccount account);
}
