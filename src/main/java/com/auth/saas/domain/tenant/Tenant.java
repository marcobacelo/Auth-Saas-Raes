package com.auth.saas.domain.tenant;

import java.util.Objects;
import java.util.UUID;

public record Tenant(UUID id, TenantSlug slug, TenantStatus status) {

    public Tenant {
        Objects.requireNonNull(id);
        Objects.requireNonNull(slug);
        Objects.requireNonNull(status);
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
