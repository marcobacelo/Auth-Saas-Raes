package com.auth.saas;

import com.auth.saas.infra.persistence.JdbcTenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTenantRepositorySlugConflictTest {

    @Test
    void slugDetailMessageMapsToTenantExists() {
        DuplicateKeyException slugConflict = new DuplicateKeyException(
                "duplicate",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"tenants_slug_key\"\n"
                                + "Detail: Key (slug)=(acme) already exists."));

        assertThat(JdbcTenantRepository.isSlugUniqueViolation(slugConflict)).isTrue();
    }

    @Test
    void primaryKeyDetailMessageDoesNotMapToTenantExists() {
        DuplicateKeyException pkConflict = new DuplicateKeyException(
                "duplicate",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"tenants_pkey\"\n"
                                + "Detail: Key (tenant_id)=(11111111-1111-1111-1111-111111111111) already exists."));

        assertThat(JdbcTenantRepository.isSlugUniqueViolation(pkConflict)).isFalse();
    }
}
