package com.auth.saas.infra.crypto;

import com.auth.saas.domain.identity.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(char[] password) {
        return encoder.encode(new String(password));
    }

    @Override
    public boolean matches(char[] password, String passwordHash) {
        return encoder.matches(new String(password), passwordHash);
    }
}
