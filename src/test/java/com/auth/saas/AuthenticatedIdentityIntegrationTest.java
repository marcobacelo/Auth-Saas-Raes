package com.auth.saas;

import com.auth.saas.infra.devdata.DevDataSeeder;
import com.auth.saas.infra.token.RsaKeyMaterial;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class AuthenticatedIdentityIntegrationTest {

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
    RsaKeyMaterial rsaKeyMaterial;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void ca01_validTokenReturnsAuthenticatedIdentity() throws Exception {
        String token = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(DevDataSeeder.ENABLED_IDENTITY_ID.toString()))
                .andExpect(jsonPath("$.username").value(DevDataSeeder.ENABLED_USERNAME))
                .andExpect(jsonPath("$.tid").value(DevDataSeeder.ACTIVE_TENANT_ID.toString()))
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.scope").doesNotExist());
    }

    @Test
    void ca02_missingBearerReturnsInvalidToken() throws Exception {
        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ca03_malformedBearerReturnsInvalidToken() throws Exception {
        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ca04_invalidSignatureReturnsInvalidToken() throws Exception {
        String forged = signWithOtherKey(
                DevDataSeeder.ENABLED_IDENTITY_ID,
                DevDataSeeder.ACTIVE_TENANT_ID,
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ca05_expiredTokenReturnsInvalidToken() throws Exception {
        String expired = signWithAppKey(
                DevDataSeeder.ENABLED_IDENTITY_ID,
                DevDataSeeder.ACTIVE_TENANT_ID,
                Instant.now().minusSeconds(60));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ca06_crossTenantReturnsInvalidTokenNotTenantNotFound() throws Exception {
        String tokenA = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        ensureSecondActiveTenant();

        MvcResult crossTenant = mockMvc.perform(get("/t/{slug}/v1/me", "other")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andReturn();

        MvcResult missingBearer = mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(crossTenant.getResponse().getStatus()).isEqualTo(missingBearer.getResponse().getStatus());
        assertThat(crossTenant.getResponse().getContentAsString())
                .isEqualTo(missingBearer.getResponse().getContentAsString());
        assertThat(crossTenant.getResponse().getContentAsString()).doesNotContain("TENANT_NOT_FOUND");
    }

    @Test
    void ca07_unknownTenantReturnsTenantNotFound() throws Exception {
        String token = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        mockMvc.perform(get("/t/{slug}/v1/me", "missing-tenant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void ca08_inactiveTenantIndistinguishableFromUnknown() throws Exception {
        String token = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        jdbcClient.sql("UPDATE tenants SET status = 'INACTIVE' WHERE tenant_id = :id")
                .param("id", DevDataSeeder.ACTIVE_TENANT_ID)
                .update();

        try {
            MvcResult inactive = mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
                    .andReturn();

            MvcResult unknown = mockMvc.perform(get("/t/{slug}/v1/me", "missing-tenant")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(inactive.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus());
            assertThat(inactive.getResponse().getContentAsString())
                    .isEqualTo(unknown.getResponse().getContentAsString());
        } finally {
            jdbcClient.sql("UPDATE tenants SET status = 'ACTIVE' WHERE tenant_id = :id")
                    .param("id", DevDataSeeder.ACTIVE_TENANT_ID)
                    .update();
        }
    }

    @Test
    void ca09_disabledIdentityStillAcceptedUntilExpiry() throws Exception {
        String token = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        jdbcClient.sql("UPDATE identities SET enabled = FALSE WHERE identity_id = :id")
                .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                .update();

        try {
            mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sub").value(DevDataSeeder.ENABLED_IDENTITY_ID.toString()))
                    .andExpect(jsonPath("$.username").value(DevDataSeeder.ENABLED_USERNAME))
                    .andExpect(jsonPath("$.tid").value(DevDataSeeder.ACTIVE_TENANT_ID.toString()));
        } finally {
            jdbcClient.sql("UPDATE identities SET enabled = TRUE WHERE identity_id = :id")
                    .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                    .update();
        }
    }

    @Test
    void ca10_removedIdentityReturnsInvalidToken() throws Exception {
        String token = login(DevDataSeeder.ACTIVE_TENANT_SLUG, DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD);

        var snapshot = jdbcClient.sql("""
                        SELECT identity_id, tenant_id, username, enabled, password_hash
                        FROM identities WHERE identity_id = :id
                        """)
                .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                .query((rs, rowNum) -> new Object[] {
                        rs.getObject("identity_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("username"),
                        rs.getBoolean("enabled"),
                        rs.getString("password_hash")
                })
                .single();

        jdbcClient.sql("DELETE FROM identities WHERE identity_id = :id")
                .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                .update();

        try {
            mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        } finally {
            jdbcClient.sql("""
                            INSERT INTO identities (identity_id, tenant_id, username, enabled, password_hash)
                            VALUES (:id, :tenantId, :username, :enabled, :passwordHash)
                            """)
                    .param("id", snapshot[0])
                    .param("tenantId", snapshot[1])
                    .param("username", snapshot[2])
                    .param("enabled", snapshot[3])
                    .param("passwordHash", snapshot[4])
                    .update();
        }
    }

    @Test
    void ca11_loginRemainsPublicWithoutBearer() throws Exception {
        mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void missingSubClaimReturnsInvalidToken() throws Exception {
        String token = signClaims(null, DevDataSeeder.ACTIVE_TENANT_ID.toString(), Instant.now().plusSeconds(900));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void missingTidClaimReturnsInvalidToken() throws Exception {
        String token = signClaims(DevDataSeeder.ENABLED_IDENTITY_ID.toString(), null, Instant.now().plusSeconds(900));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void invalidSubFormatReturnsInvalidToken() throws Exception {
        String token = signClaims("not-a-uuid", DevDataSeeder.ACTIVE_TENANT_ID.toString(), Instant.now().plusSeconds(900));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void invalidTidFormatReturnsInvalidToken() throws Exception {
        String token = signClaims(DevDataSeeder.ENABLED_IDENTITY_ID.toString(), "not-a-uuid", Instant.now().plusSeconds(900));

        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private String login(String slug, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/t/{slug}/v1/auth/login", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void ensureSecondActiveTenant() {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM tenants WHERE slug = 'other'")
                .query(Integer.class)
                .single();
        if (count != null && count > 0) {
            jdbcClient.sql("UPDATE tenants SET status = 'ACTIVE' WHERE slug = 'other'").update();
            return;
        }
        jdbcClient.sql("""
                        INSERT INTO tenants (tenant_id, slug, status)
                        VALUES (:id, 'other', 'ACTIVE')
                        """)
                .param("id", UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .update();
    }

    private String signWithAppKey(UUID subject, UUID tenantId, Instant expiresAt) throws Exception {
        return signClaims(subject.toString(), tenantId.toString(), expiresAt);
    }

    private static String signWithOtherKey(UUID subject, UUID tenantId, Instant expiresAt) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        return sign((RSAPrivateKey) other.getPrivate(), subject.toString(), tenantId.toString(), expiresAt);
    }

    private String signClaims(String subject, String tenantId, Instant expiresAt) throws Exception {
        return sign(rsaKeyMaterial.privateKey(), subject, tenantId, expiresAt);
    }

    private static String sign(RSAPrivateKey privateKey, String subject, String tenantId, Instant expiresAt)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(expiresAt));
        if (subject != null) {
            builder.subject(subject);
        }
        if (tenantId != null) {
            builder.claim("tid", tenantId);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                builder.build());
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}
