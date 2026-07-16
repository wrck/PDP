package com.pdp.identity.infrastructure.oidc;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;

public record OidcLoginResult(
    UserAccount account,
    ExternalIdentity identity,
    String identityProviderSessionId,
    String redirectUri) {}
