package com.auth.saas;

import com.auth.saas.infra.devdata.DevDataSeeder;
import com.jayway.jsonpath.JsonPath;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
class PlatformTenantProvisioningIntegrationTest {

    private static final String PLATFORM_USER = "platform-test-operator";
    private static final String PLATFORM_PASSWORD = "platform-test-secret";
    private static final String INITIAL_PASSWORD = "ProvisionedPass1!";

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
    void ca01_validProvisionCreatesActiveTenantAndEnabledIdentity() throws Exception {
        String slug = "tenant-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"%s"}
                                """.formatted(slug, INITIAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").isString())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.identityId").isString())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String tenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.tenantId");
        String identityId = JsonPath.read(result.getResponse().getContentAsString(), "$.identityId");

        String status = jdbcClient.sql("SELECT status FROM tenants WHERE tenant_id = :id")
                .param("id", UUID.fromString(tenantId))
                .query(String.class)
                .single();
        assertThat(status).isEqualTo("ACTIVE");

        Boolean enabled = jdbcClient.sql("SELECT enabled FROM identities WHERE identity_id = :id")
                .param("id", UUID.fromString(identityId))
                .query(Boolean.class)
                .single();
        assertThat(enabled).isTrue();
    }

    @Test
    void ca02_passwordStoredAsArgon2idNeverReturned() throws Exception {
        String slug = "argon-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"%s"}
                                """.formatted(slug, INITIAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(INITIAL_PASSWORD);
        assertThat(body).doesNotContain("passwordHash");

        String identityId = JsonPath.read(body, "$.identityId");
        String hash = jdbcClient.sql("SELECT password_hash FROM identities WHERE identity_id = :id")
                .param("id", UUID.fromString(identityId))
                .query(String.class)
                .single();
        assertThat(hash).startsWith("$argon2");
        assertThat(hash).doesNotContain(INITIAL_PASSWORD);
    }

    @Test
    void ca03_and_ca04_loginAndMeWithProvisionedCredentials() throws Exception {
        String slug = "flow-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult provisioned = mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"%s"}
                                """.formatted(slug, INITIAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenantId = JsonPath.read(provisioned.getResponse().getContentAsString(), "$.tenantId");
        String identityId = JsonPath.read(provisioned.getResponse().getContentAsString(), "$.identityId");

        MvcResult login = mockMvc.perform(post("/t/{slug}/v1/auth/login", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","password":"%s"}
                                """.formatted(INITIAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/t/{slug}/v1/me", slug)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(identityId))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.tid").value(tenantId));
    }

    @Test
    void ca05_missingBasicReturnsPlatformUnauthorized() throws Exception {
        String slug = "nobasic-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/platform/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"%s"}
                                """.formatted(slug, INITIAL_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLATFORM_UNAUTHORIZED"));

        assertThat(tenantCount(slug)).isZero();
    }

    @Test
    void ca06_invalidBasicReturnsPlatformUnauthorized() throws Exception {
        String slug = "badbasic-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("wrong", "credentials"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"%s"}
                                """.formatted(slug, INITIAL_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLATFORM_UNAUTHORIZED"));

        assertThat(tenantCount(slug)).isZero();
    }

    @Test
    void ca07_duplicateSlugReturnsTenantExistsWithoutSideEffects() throws Exception {
        Integer identitiesBefore = jdbcClient.sql("""
                        SELECT COUNT(*) FROM identities WHERE tenant_id = :tenantId
                        """)
                .param("tenantId", DevDataSeeder.ACTIVE_TENANT_ID)
                .query(Integer.class)
                .single();
        String statusBefore = jdbcClient.sql("SELECT status FROM tenants WHERE tenant_id = :id")
                .param("id", DevDataSeeder.ACTIVE_TENANT_ID)
                .query(String.class)
                .single();
        String hashBefore = jdbcClient.sql("""
                        SELECT password_hash FROM identities WHERE identity_id = :id
                        """)
                .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                .query(String.class)
                .single();

        mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"other","password":"%s"}
                                """.formatted(DevDataSeeder.ACTIVE_TENANT_SLUG, INITIAL_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_EXISTS"));

        Integer identitiesAfter = jdbcClient.sql("""
                        SELECT COUNT(*) FROM identities WHERE tenant_id = :tenantId
                        """)
                .param("tenantId", DevDataSeeder.ACTIVE_TENANT_ID)
                .query(Integer.class)
                .single();
        String statusAfter = jdbcClient.sql("SELECT status FROM tenants WHERE tenant_id = :id")
                .param("id", DevDataSeeder.ACTIVE_TENANT_ID)
                .query(String.class)
                .single();
        String hashAfter = jdbcClient.sql("""
                        SELECT password_hash FROM identities WHERE identity_id = :id
                        """)
                .param("id", DevDataSeeder.ENABLED_IDENTITY_ID)
                .query(String.class)
                .single();

        assertThat(identitiesAfter).isEqualTo(identitiesBefore);
        assertThat(statusAfter).isEqualTo(statusBefore);
        assertThat(hashAfter).isEqualTo(hashBefore);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM identities WHERE username = 'other'")
                .query(Integer.class)
                .single()).isZero();
    }

    @Test
    void unknownPlatformPathDeniedEvenWithValidBasic() throws Exception {
        mockMvc.perform(get("/platform/v1/unknown")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPathOutsideTenantAndPlatformIsDenied() throws Exception {
        mockMvc.perform(get("/not-a-known-surface"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ca08_shortPasswordReturnsInvalidRequest() throws Exception {
        String slug = "shortpw-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","username":"owner","password":"short"}
                                """.formatted(slug)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(tenantCount(slug)).isZero();
    }

    @Test
    void ca09_invalidRequestVariantsReturnInvalidRequest() throws Exception {
        assertInvalidRequest("{\"slug\":\"\",\"username\":\"owner\",\"password\":\"" + INITIAL_PASSWORD + "\"}");
        assertInvalidRequest("{\"slug\":\"INVALID_SLUG\",\"username\":\"owner\",\"password\":\"" + INITIAL_PASSWORD + "\"}");
        assertInvalidRequest("{\"slug\":\"ok-slug\",\"username\":\"\",\"password\":\"" + INITIAL_PASSWORD + "\"}");
        assertInvalidRequest("{\"slug\":\"ok-slug2\",\"username\":\"owner\",\"password\":\"\"}");
        assertInvalidRequest("{\"username\":\"owner\",\"password\":\"" + INITIAL_PASSWORD + "\"}");
    }

    @Test
    void ca12_platformBasicDoesNotAuthenticateMe() throws Exception {
        mockMvc.perform(get("/t/{slug}/v1/me", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void ca12_bearerDoesNotAuthenticatePlatform() throws Exception {
        MvcResult login = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"bearer-block","username":"owner","password":"%s"}
                                """.formatted(INITIAL_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLATFORM_UNAUTHORIZED"));
    }

    private void assertInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private int tenantCount(String slug) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tenants WHERE slug = :slug")
                .param("slug", slug)
                .query(Integer.class)
                .single();
    }

    private static String basicAuth() {
        return basicAuth(PLATFORM_USER, PLATFORM_PASSWORD);
    }

    private static String basicAuth(String username, String password) {
        String token = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
