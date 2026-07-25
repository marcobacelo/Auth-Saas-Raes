package com.auth.saas.domain.identity;

public interface PasswordHasher {

    String hash(char[] password);

    boolean matches(char[] password, String passwordHash);
}
