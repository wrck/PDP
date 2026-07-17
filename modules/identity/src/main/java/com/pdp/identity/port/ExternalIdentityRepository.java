package com.pdp.identity.port;

import com.pdp.identity.domain.ExternalIdentity;

import java.util.Optional;
import java.util.UUID;

/**
 * 外部身份绑定仓储端口。
 */
public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);

    Optional<ExternalIdentity> findById(UUID id);

    java.util.List<ExternalIdentity> findByUserId(UUID userId);

    void save(ExternalIdentity identity);

    void unbind(UUID id, int expectedRevision);
}
