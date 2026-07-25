package com.auth.saas;

import com.auth.saas.infra.crypto.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    void ca09_hashesWithArgon2idAndVerifies() {
        char[] password = "ChangeMeNow1!".toCharArray();
        String hash = hasher.hash(password);

        assertThat(hash).doesNotContain("ChangeMeNow1!");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(hasher.matches("ChangeMeNow1!".toCharArray(), hash)).isTrue();
        assertThat(hasher.matches("wrong".toCharArray(), hash)).isFalse();
    }
}
