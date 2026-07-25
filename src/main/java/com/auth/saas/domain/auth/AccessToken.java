package com.auth.saas.domain.auth;

import java.util.Objects;
import java.util.UUID;

public record AccessToken(String value, UUID subjectId, UUID tenantId) {

    public AccessToken {
        Objects.requireNonNull(value);
        Objects.requireNonNull(subjectId);
        Objects.requireNonNull(tenantId);
    }
}
