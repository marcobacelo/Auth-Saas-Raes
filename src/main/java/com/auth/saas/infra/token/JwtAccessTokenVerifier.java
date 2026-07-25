package com.auth.saas.infra.token;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtAccessTokenVerifier {

    private final RSAPublicKey publicKey;
    private final Clock clock;

    public JwtAccessTokenVerifier(RsaKeyMaterial keyMaterial, Clock clock) {
        this.publicKey = keyMaterial.publicKey();
        this.clock = clock;
    }

    public VerifiedAccessToken verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new IllegalArgumentException("invalid token signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.after(Date.from(clock.instant()))) {
                throw new IllegalArgumentException("token expired");
            }

            String subject = claims.getSubject();
            String tenantId = claims.getStringClaim("tid");
            if (subject == null || tenantId == null) {
                throw new IllegalArgumentException("token missing required claims");
            }

            return new VerifiedAccessToken(UUID.fromString(subject), UUID.fromString(tenantId), expiration.toInstant());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid token", ex);
        }
    }

    public record VerifiedAccessToken(UUID subjectId, UUID tenantId, java.time.Instant expiresAt) {
    }
}
