package com.auth.saas.infra.persistence;

import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {

    private final JdbcClient jdbcClient;

    public JdbcIdentityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Identity> findByTenantIdAndUsername(UUID tenantId, String username) {
        return jdbcClient.sql("""
                        SELECT identity_id, tenant_id, username, enabled, password_hash
                        FROM identities
                        WHERE tenant_id = :tenantId AND username = :username
                        """)
                .param("tenantId", tenantId)
                .param("username", username)
                .query(this::mapIdentity)
                .optional();
    }

    @Override
    public Optional<Identity> findByTenantIdAndId(UUID tenantId, UUID identityId) {
        return jdbcClient.sql("""
                        SELECT identity_id, tenant_id, username, enabled, password_hash
                        FROM identities
                        WHERE tenant_id = :tenantId AND identity_id = :identityId
                        """)
                .param("tenantId", tenantId)
                .param("identityId", identityId)
                .query(this::mapIdentity)
                .optional();
    }

    private Identity mapIdentity(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Identity(
                rs.getObject("identity_id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("username"),
                rs.getBoolean("enabled"),
                rs.getString("password_hash"));
    }
}
