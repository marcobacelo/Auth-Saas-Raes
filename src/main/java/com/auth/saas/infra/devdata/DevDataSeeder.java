package com.auth.saas.infra.devdata;

import com.auth.saas.domain.identity.PasswordHasher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development/test-only seed. Not an onboarding API and not for production provisioning.
 */
@Component
@ConditionalOnProperty(prefix = "auth.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    public static final UUID ACTIVE_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID INACTIVE_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ENABLED_IDENTITY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID DISABLED_IDENTITY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    public static final String ACTIVE_TENANT_SLUG = "acme";
    public static final String INACTIVE_TENANT_SLUG = "paused";
    public static final String ENABLED_USERNAME = "admin";
    public static final String DISABLED_USERNAME = "disabled-user";
    public static final String PASSWORD = "ChangeMeNow1!";

    private final JdbcClient jdbcClient;
    private final PasswordHasher passwordHasher;

    public DevDataSeeder(JdbcClient jdbcClient, PasswordHasher passwordHasher) {
        this.jdbcClient = jdbcClient;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer tenantCount = jdbcClient.sql("SELECT COUNT(*) FROM tenants")
                .query(Integer.class)
                .single();
        if (tenantCount != null && tenantCount > 0) {
            return;
        }

        String passwordHash = passwordHasher.hash(PASSWORD.toCharArray());

        jdbcClient.sql("""
                        INSERT INTO tenants (tenant_id, slug, status)
                        VALUES (:id, :slug, :status)
                        """)
                .param("id", ACTIVE_TENANT_ID)
                .param("slug", ACTIVE_TENANT_SLUG)
                .param("status", "ACTIVE")
                .update();

        jdbcClient.sql("""
                        INSERT INTO tenants (tenant_id, slug, status)
                        VALUES (:id, :slug, :status)
                        """)
                .param("id", INACTIVE_TENANT_ID)
                .param("slug", INACTIVE_TENANT_SLUG)
                .param("status", "INACTIVE")
                .update();

        jdbcClient.sql("""
                        INSERT INTO identities (identity_id, tenant_id, username, enabled, password_hash)
                        VALUES (:id, :tenantId, :username, :enabled, :passwordHash)
                        """)
                .param("id", ENABLED_IDENTITY_ID)
                .param("tenantId", ACTIVE_TENANT_ID)
                .param("username", ENABLED_USERNAME)
                .param("enabled", true)
                .param("passwordHash", passwordHash)
                .update();

        jdbcClient.sql("""
                        INSERT INTO identities (identity_id, tenant_id, username, enabled, password_hash)
                        VALUES (:id, :tenantId, :username, :enabled, :passwordHash)
                        """)
                .param("id", DISABLED_IDENTITY_ID)
                .param("tenantId", ACTIVE_TENANT_ID)
                .param("username", DISABLED_USERNAME)
                .param("enabled", false)
                .param("passwordHash", passwordHash)
                .update();
    }
}
