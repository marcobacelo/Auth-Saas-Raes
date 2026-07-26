package com.auth.saas.infra.config;

import com.auth.saas.domain.auth.AccessTokenIssuer;
import com.auth.saas.domain.auth.AuthenticateWithPassword;
import com.auth.saas.domain.auth.GetAuthenticatedIdentity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.domain.identity.PasswordHasher;
import com.auth.saas.domain.tenant.TenantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    AuthenticateWithPassword authenticateWithPassword(
            TenantRepository tenantRepository,
            IdentityRepository identityRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer) {
        return new AuthenticateWithPassword(
                tenantRepository, identityRepository, passwordHasher, accessTokenIssuer);
    }

    @Bean
    GetAuthenticatedIdentity getAuthenticatedIdentity(
            TenantRepository tenantRepository,
            IdentityRepository identityRepository) {
        return new GetAuthenticatedIdentity(tenantRepository, identityRepository);
    }
}
