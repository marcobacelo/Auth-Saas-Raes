package com.auth.saas.domain.identity;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {

    Optional<Identity> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<Identity> findByTenantIdAndId(UUID tenantId, UUID identityId);

    void save(Identity identity);
}
