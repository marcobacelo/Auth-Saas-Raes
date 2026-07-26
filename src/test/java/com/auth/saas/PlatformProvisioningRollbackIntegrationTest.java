package com.auth.saas;

import com.auth.saas.domain.identity.Identity;
import com.auth.saas.domain.identity.IdentityRepository;
import com.auth.saas.infra.persistence.JdbcIdentityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "auth.seed.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
@org.springframework.context.annotation.Import(PlatformProvisioningRollbackIntegrationTest.FailingIdentitySaveConfig.class)
class PlatformProvisioningRollbackIntegrationTest {

    private static final String PLATFORM_USER = "platform-test-operator";
    private static final String PLATFORM_PASSWORD = "platform-test-secret";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void ca09_identitySaveFailureRollsBackTenant() throws Exception {
        String slug = "rollback-tenant";

        assertThatThrownBy(() -> mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"RollbackPass12"}
                                """.formatted(slug)))
                .andReturn())
                .isInstanceOf(jakarta.servlet.ServletException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);

        Integer tenants = jdbcClient.sql("SELECT COUNT(*) FROM tenants WHERE slug = :slug")
                .param("slug", slug)
                .query(Integer.class)
                .single();
        Integer identities = jdbcClient.sql("SELECT COUNT(*) FROM identities").query(Integer.class).single();

        assertThat(tenants).as("tenant must not remain after identity save failure").isZero();
        assertThat(identities).as("no orphan identity").isZero();
    }

    @TestConfiguration
    static class FailingIdentitySaveConfig {

        @Bean
        @Primary
        IdentityRepository failingIdentityRepository(JdbcClient jdbcClient) {
            IdentityRepository real = new JdbcIdentityRepository(jdbcClient);
            return new IdentityRepository() {
                @Override
                public Optional<Identity> findByTenantIdAndUsername(UUID tenantId, String username) {
                    return real.findByTenantIdAndUsername(tenantId, username);
                }

                @Override
                public Optional<Identity> findByTenantIdAndId(UUID tenantId, UUID identityId) {
                    return real.findByTenantIdAndId(tenantId, identityId);
                }

                @Override
                public void save(Identity identity) {
                    throw new IllegalStateException("forced identity save failure for rollback test");
                }
            };
        }
    }

    private static String basicAuth() {
        String token = Base64.getEncoder()
                .encodeToString((PLATFORM_USER + ":" + PLATFORM_PASSWORD).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
