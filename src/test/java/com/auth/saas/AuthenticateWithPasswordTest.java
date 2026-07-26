package com.auth.saas;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.auth.AccessToken;
import com.auth.saas.domain.auth.AccessTokenIssuer;
import com.auth.saas.domain.auth.AuthenticateWithPassword;
import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.domain.identity.PasswordHasher;
import com.auth.saas.domain.tenant.Tenant;
import com.auth.saas.domain.tenant.TenantRepository;
import com.auth.saas.domain.tenant.TenantSlug;
import com.auth.saas.domain.tenant.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateWithPasswordTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IDENTITY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String SLUG = "acme";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "ChangeMeNow1!";

    private final CountingPasswordHasher passwordHasher = new CountingPasswordHasher();

    @Test
    void unknownIdentityStillRunsPasswordVerification() {
        AuthenticateWithPassword useCase = useCaseWith(Optional.empty());
        int hashCallsAfterConstruction = passwordHasher.hashCalls;

        assertThatThrownBy(() -> useCase.authenticate(SLUG, "nobody", PASSWORD))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo(AuthenticateWithPassword.INVALID_CREDENTIALS);

        assertThat(passwordHasher.matchesCalls).isEqualTo(1);
        assertThat(passwordHasher.hashCalls)
                .as("no new Argon2id hash is computed per failed attempt")
                .isEqualTo(hashCallsAfterConstruction);
    }

    @Test
    void disabledIdentityStillRunsPasswordVerification() {
        AuthenticateWithPassword useCase = useCaseWith(Optional.of(identity(false)));

        assertThatThrownBy(() -> useCase.authenticate(SLUG, USERNAME, PASSWORD))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo(AuthenticateWithPassword.INVALID_CREDENTIALS);

        assertThat(passwordHasher.matchesCalls).isEqualTo(1);
    }

    @Test
    void wrongPasswordRunsPasswordVerification() {
        AuthenticateWithPassword useCase = useCaseWith(Optional.of(identity(true)));

        assertThatThrownBy(() -> useCase.authenticate(SLUG, USERNAME, "wrong-password"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo(AuthenticateWithPassword.INVALID_CREDENTIALS);

        assertThat(passwordHasher.matchesCalls).isEqualTo(1);
    }

    @Test
    void validCredentialsIssueAccessToken() {
        AuthenticateWithPassword useCase = useCaseWith(Optional.of(identity(true)));

        AccessToken token = useCase.authenticate(SLUG, USERNAME, PASSWORD);

        assertThat(token.subjectId()).isEqualTo(IDENTITY_ID);
        assertThat(token.tenantId()).isEqualTo(TENANT_ID);
    }

    private AuthenticateWithPassword useCaseWith(Optional<Identity> identity) {
        TenantRepository tenantRepository = new TenantRepository() {
            @Override
            public Optional<Tenant> findBySlug(TenantSlug slug) {
                return Optional.of(new Tenant(TENANT_ID, new TenantSlug(SLUG), TenantStatus.ACTIVE));
            }

            @Override
            public void save(Tenant tenant) {
                throw new UnsupportedOperationException();
            }
        };
        IdentityRepository identityRepository = new IdentityRepository() {
            @Override
            public Optional<Identity> findByTenantIdAndUsername(UUID tenantId, String username) {
                return identity;
            }

            @Override
            public Optional<Identity> findByTenantIdAndId(UUID tenantId, UUID identityId) {
                return Optional.empty();
            }

            @Override
            public void save(Identity identityToSave) {
                throw new UnsupportedOperationException();
            }
        };
        AccessTokenIssuer issuer = (subjectId, tenantId) -> new AccessToken("token", subjectId, tenantId);
        return new AuthenticateWithPassword(tenantRepository, identityRepository, passwordHasher, issuer);
    }

    private static Identity identity(boolean enabled) {
        return new Identity(IDENTITY_ID, TENANT_ID, USERNAME, enabled, "hash:" + PASSWORD);
    }

    private static final class CountingPasswordHasher implements PasswordHasher {

        private int hashCalls;
        private int matchesCalls;

        @Override
        public String hash(char[] password) {
            hashCalls++;
            return "hash:" + new String(password);
        }

        @Override
        public boolean matches(char[] password, String passwordHash) {
            matchesCalls++;
            return passwordHash.equals("hash:" + new String(password));
        }
    }
}
