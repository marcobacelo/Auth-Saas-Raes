package com.auth.saas;

import com.auth.saas.infra.devdata.DevDataSeeder;
import com.auth.saas.infra.token.JwtAccessTokenVerifier;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class PasswordLoginIntegrationTest {

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
    JwtAccessTokenVerifier tokenVerifier;

    @Test
    void ca01_validLoginReturnsVerifiableAccessTokenWithoutRefresh() throws Exception {
        MvcResult result = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        var verified = tokenVerifier.verify(accessToken);
        assertThat(verified.subjectId()).isEqualTo(DevDataSeeder.ENABLED_IDENTITY_ID);
        assertThat(verified.tenantId()).isEqualTo(DevDataSeeder.ACTIVE_TENANT_ID);
        assertThat(verified.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void ca02_invalidPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong-password"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void ca03_unknownIdentityIndistinguishableFromInvalidPassword() throws Exception {
        MvcResult unknown = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"%s"}
                                """.formatted(DevDataSeeder.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong-password"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void ca04_unknownTenantReturnsNotFound() throws Exception {
        mockMvc.perform(post("/t/{slug}/v1/auth/login", "missing-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void ca05_inactiveTenantIndistinguishableFromUnknownTenant() throws Exception {
        MvcResult inactive = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.INACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        MvcResult unknown = mockMvc.perform(post("/t/{slug}/v1/auth/login", "missing-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(inactive.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus());
        assertThat(inactive.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void ca06_disabledIdentityIndistinguishableFromInvalidPassword() throws Exception {
        MvcResult disabled = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.DISABLED_USERNAME, DevDataSeeder.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/t/{slug}/v1/auth/login", DevDataSeeder.ACTIVE_TENANT_SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong-password"}
                                """.formatted(DevDataSeeder.ENABLED_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(disabled.getResponse().getStatus()).isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(disabled.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }
}
