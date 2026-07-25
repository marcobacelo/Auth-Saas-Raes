package com.auth.saas.domain.auth;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.domain.identity.PasswordHasher;
import com.auth.saas.domain.tenant.Tenant;
import com.auth.saas.domain.tenant.TenantRepository;
import com.auth.saas.domain.tenant.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public class AuthenticateWithPassword {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";

    private final TenantRepository tenantRepository;
    private final IdentityRepository identityRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;

    /**
     * Hash computed once so that attempts against unknown identities still pay the Argon2id
     * verification cost, keeping failure paths computationally comparable.
     */
    private final String decoyPasswordHash;

    public AuthenticateWithPassword(
            TenantRepository tenantRepository,
            IdentityRepository identityRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer) {
        this.tenantRepository = tenantRepository;
        this.identityRepository = identityRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.decoyPasswordHash = passwordHasher.hash(UUID.randomUUID().toString().toCharArray());
    }

    public AccessToken authenticate(String tenantSlug, String username, String password) {
        TenantSlug slug;
        try {
            slug = new TenantSlug(tenantSlug);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(TENANT_NOT_FOUND);
        }

        Tenant tenant = tenantRepository.findBySlug(slug)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new DomainException(TENANT_NOT_FOUND));

        Optional<Identity> found = identityRepository.findByTenantIdAndUsername(tenant.id(), username);
        if (found.isEmpty()) {
            passwordHasher.matches(password.toCharArray(), decoyPasswordHash);
            throw new DomainException(INVALID_CREDENTIALS);
        }

        Identity identity = found.get();
        boolean passwordMatches = passwordHasher.matches(password.toCharArray(), identity.passwordHash());
        if (!identity.enabled() || !passwordMatches) {
            throw new DomainException(INVALID_CREDENTIALS);
        }

        return accessTokenIssuer.issue(identity.id(), tenant.id());
    }
}
