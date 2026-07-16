package com.pdp.mysql.identity;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.port.ExternalIdentityRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlExternalIdentityRepository implements ExternalIdentityRepository {

  private final ExternalIdentityMapper mapper;

  public MysqlExternalIdentityRepository(ExternalIdentityMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject) {
    return Optional.ofNullable(mapper.findByIssuerAndSubject(issuer, subject))
        .map(ExternalIdentityRow::toDomain);
  }

  @Override
  public ExternalIdentity save(ExternalIdentity identity) {
    ExternalIdentityRow row = ExternalIdentityRow.fromDomain(identity);
    if (mapper.update(row) == 0) {
      mapper.insert(row);
    }
    return mapper.findByIssuerAndSubject(identity.issuer(), identity.subject()).toDomain();
  }
}
