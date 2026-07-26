package com.auth.saas.domain.tenant;

import java.util.UUID;

public record ProvisionedTenant(
        UUID tenantId,
        String slug,
        UUID identityId,
        String username) {
}
