package com.auth.saas.domain.tenant;

import java.util.Optional;

public interface TenantRepository {

    Optional<Tenant> findBySlug(TenantSlug slug);

    void save(Tenant tenant);
}
