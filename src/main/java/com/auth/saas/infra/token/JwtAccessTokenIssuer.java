package com.auth.saas.infra.token;

import com.auth.saas.domain.auth.AccessToken;
import com.auth.saas.domain.auth.AccessTokenIssuer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final RsaKeyMaterial keyMaterial;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(RsaKeyMaterial keyMaterial, JwtProperties properties, Clock clock) {
        this.keyMaterial = keyMaterial;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(UUID subjectId, UUID tenantId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.accessTokenTtlSeconds());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subjectId.toString())
                .claim("tid", tenantId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(keyMaterial.privateKey()));
            return new AccessToken(jwt.serialize(), subjectId, tenantId);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign access token", ex);
        }
    }
}
