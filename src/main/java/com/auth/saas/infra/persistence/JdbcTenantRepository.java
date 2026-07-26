package com.auth.saas.infra.persistence;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.tenant.ProvisionTenant;
import com.auth.saas.domain.tenant.Tenant;
import com.auth.saas.domain.tenant.TenantRepository;
import com.auth.saas.domain.tenant.TenantSlug;
import com.auth.saas.domain.tenant.TenantStatus;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    public static final String TENANTS_SLUG_UNIQUE = "tenants_slug_key";

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

    @Override
    public void save(Tenant tenant) {
        try {
            jdbcClient.sql("""
                            INSERT INTO tenants (tenant_id, slug, status)
                            VALUES (:id, :slug, :status)
                            """)
                    .param("id", tenant.id())
                    .param("slug", tenant.slug().value())
                    .param("status", tenant.status().name())
                    .update();
        } catch (DuplicateKeyException ex) {
            if (isSlugUniqueViolation(ex)) {
                throw new DomainException(ProvisionTenant.TENANT_EXISTS);
            }
            throw ex;
        }
    }

    public static boolean isSlugUniqueViolation(DuplicateKeyException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
            return TENANTS_SLUG_UNIQUE.equals(psql.getServerErrorMessage().getConstraint());
        }
        String message = cause.getMessage();
        return message != null && message.contains("(slug)=");
    }
}
