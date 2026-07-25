package com.auth.saas.domain.auth;

import java.util.UUID;

public interface AccessTokenIssuer {

    AccessToken issue(UUID subjectId, UUID tenantId);
}
