package com.auth.saas.domain.auth;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.domain.tenant.Tenant;
import com.auth.saas.domain.tenant.TenantRepository;
import com.auth.saas.domain.tenant.TenantSlug;

import java.util.UUID;

public class GetAuthenticatedIdentity {

    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";

    private final TenantRepository tenantRepository;
    private final IdentityRepository identityRepository;

    public GetAuthenticatedIdentity(TenantRepository tenantRepository, IdentityRepository identityRepository) {
        this.tenantRepository = tenantRepository;
        this.identityRepository = identityRepository;
    }

    public AuthenticatedIdentity get(String tenantSlug, UUID subjectId, UUID tokenTenantId) {
        TenantSlug slug;
        try {
            slug = new TenantSlug(tenantSlug);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(TENANT_NOT_FOUND);
        }

        Tenant tenant = tenantRepository.findBySlug(slug)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new DomainException(TENANT_NOT_FOUND));

        if (!tenant.id().equals(tokenTenantId)) {
            throw new DomainException(INVALID_TOKEN);
        }

        Identity identity = identityRepository.findByTenantIdAndId(tenant.id(), subjectId)
                .orElseThrow(() -> new DomainException(INVALID_TOKEN));

        return new AuthenticatedIdentity(identity.id(), identity.username(), tenant.id());
    }

    public record AuthenticatedIdentity(UUID sub, String username, UUID tid) {
    }
}
