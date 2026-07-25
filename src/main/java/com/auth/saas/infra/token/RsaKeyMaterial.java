package com.auth.saas.infra.token;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Holds the RSA key pair used to sign and verify access tokens.
 * When {@code auth.jwt.ephemeral-keys=true} (dev/test), a disposable key pair is generated at startup.
 * Production-like profiles must not rely on ephemeral keys.
 */
@Component
public class RsaKeyMaterial {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public RsaKeyMaterial(JwtProperties properties) {
        if (!properties.ephemeralKeys()) {
            throw new IllegalStateException(
                    "auth.jwt.ephemeral-keys is false and no production key source is configured in this delivery; "
                            + "enable ephemeral keys for local development/tests only");
        }
        KeyPair keyPair = generateEphemeralRsaKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    static KeyPair generateEphemeralRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key generation is unavailable", ex);
        }
    }
}
