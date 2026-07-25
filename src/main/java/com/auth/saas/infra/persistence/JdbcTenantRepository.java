package com.auth.saas.infra.persistence;

import com.auth.saas.domain.tenant.Tenant;
import com.auth.saas.domain.tenant.TenantRepository;
import com.auth.saas.domain.tenant.TenantSlug;
import com.auth.saas.domain.tenant.TenantStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    private final JdbcClient jdbcClient;

    public JdbcTenantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Tenant> findBySlug(TenantSlug slug) {
        return jdbcClient.sql("""
                        SELECT tenant_id, slug, status
                        FROM tenants
                        WHERE slug = :slug
                        """)
                .param("slug", slug.value())
                .query((rs, rowNum) -> new Tenant(
                        rs.getObject("tenant_id", UUID.class),
                        new TenantSlug(rs.getString("slug")),
                        TenantStatus.valueOf(rs.getString("status"))))
                .optional();
    }
}
