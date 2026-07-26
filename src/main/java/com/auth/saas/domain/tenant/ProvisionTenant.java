package com.auth.saas.domain.tenant;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.domain.identity.PasswordHasher;

import java.util.UUID;

public class ProvisionTenant {

    public static final String TENANT_EXISTS = "TENANT_EXISTS";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    private final TenantRepository tenantRepository;
    private final IdentityRepository identityRepository;
    private final PasswordHasher passwordHasher;

    public ProvisionTenant(
            TenantRepository tenantRepository,
            IdentityRepository identityRepository,
            PasswordHasher passwordHasher) {
        this.tenantRepository = tenantRepository;
        this.identityRepository = identityRepository;
        this.passwordHasher = passwordHasher;
    }

    public ProvisionedTenant provision(String slug, String username, String password) {
        TenantSlug tenantSlug;
        try {
            tenantSlug = new TenantSlug(slug);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DomainException(INVALID_REQUEST);
        }

        UUID tenantId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        String passwordHash = passwordHasher.hash(password.toCharArray());

        Tenant tenant = new Tenant(tenantId, tenantSlug, TenantStatus.ACTIVE);
        Identity identity = new Identity(identityId, tenantId, username, true, passwordHash);

        tenantRepository.save(tenant);
        identityRepository.save(identity);

        return new ProvisionedTenant(tenantId, tenantSlug.value(), identityId, username);
    }
}
