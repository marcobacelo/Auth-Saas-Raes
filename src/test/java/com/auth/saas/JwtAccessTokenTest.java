package com.auth.saas;

import com.auth.saas.infra.token.JwtAccessTokenIssuer;
import com.auth.saas.infra.token.JwtAccessTokenVerifier;
import com.auth.saas.infra.token.JwtProperties;
import com.auth.saas.infra.token.RsaKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenTest {

    private static final UUID SUBJECT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RsaKeyMaterial keyMaterial;
    private AtomicReference<Instant> now;

    @BeforeEach
    void setUp() {
        keyMaterial = new RsaKeyMaterial(new JwtProperties(900, true));
        now = new AtomicReference<>(Instant.parse("2026-07-25T12:00:00Z"));
    }

    private Clock clock() {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }

    @Test
    void ca07_expiredTokenFailsVerification() {
        Clock clock = clock();
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(keyMaterial, new JwtProperties(900, true), clock);
        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(keyMaterial, clock);

        String token = issuer.issue(SUBJECT, TENANT).value();
        now.set(now.get().plus(Duration.ofSeconds(901)));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void ca08_defaultAndConfigurableTtlAreReflectedInExp() {
        Clock clock = clock();
        JwtAccessTokenIssuer defaultIssuer =
                new JwtAccessTokenIssuer(keyMaterial, new JwtProperties(900, true), clock);
        var defaultToken = defaultIssuer.issue(SUBJECT, TENANT);
        var defaultVerified = new JwtAccessTokenVerifier(keyMaterial, clock).verify(defaultToken.value());
        assertThat(Duration.between(now.get(), defaultVerified.expiresAt()).getSeconds()).isEqualTo(900);

        JwtAccessTokenIssuer customIssuer =
                new JwtAccessTokenIssuer(keyMaterial, new JwtProperties(120, true), clock);
        var customToken = customIssuer.issue(SUBJECT, TENANT);
        var customVerified = new JwtAccessTokenVerifier(keyMaterial, clock).verify(customToken.value());
        assertThat(Duration.between(now.get(), customVerified.expiresAt()).getSeconds()).isEqualTo(120);
    }

    @Test
    void issuedTokenContainsRequiredClaims() {
        Clock clock = clock();
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(keyMaterial, new JwtProperties(900, true), clock);
        var verified = new JwtAccessTokenVerifier(keyMaterial, clock).verify(issuer.issue(SUBJECT, TENANT).value());
        assertThat(verified.subjectId()).isEqualTo(SUBJECT);
        assertThat(verified.tenantId()).isEqualTo(TENANT);
    }
}
