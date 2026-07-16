package com.pdp.identity.port;

import com.pdp.identity.domain.ExternalIdentity;
import java.util.Optional;

public interface ExternalIdentityRepository {
  Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);

  ExternalIdentity save(ExternalIdentity identity);
}
