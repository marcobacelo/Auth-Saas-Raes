package com.auth.saas.domain.identity;

import java.util.Objects;
import java.util.UUID;

public record Identity(
        UUID id,
        UUID tenantId,
        String username,
        boolean enabled,
        String passwordHash) {

    public Identity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(username);
        Objects.requireNonNull(passwordHash);
    }
}
