package com.auth.saas;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.seed.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class PlatformProvisioningWithoutSeederIntegrationTest {

    private static final String PLATFORM_USER = "platform-test-operator";
    private static final String PLATFORM_PASSWORD = "platform-test-secret";
    private static final String INITIAL_PASSWORD = "EmptyInstallPass1!";

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
    void ca10_ca11_emptyInstallProvisionLoginMeWithoutSeeder() throws Exception {
        Integer tenantsBefore = jdbcClient.sql("SELECT COUNT(*) FROM tenants")
                .query(Integer.class)
                .single();
        Integer identitiesBefore = jdbcClient.sql("SELECT COUNT(*) FROM identities")
                .query(Integer.class)
                .single();
        assertThat(tenantsBefore).isZero();
        assertThat(identitiesBefore).isZero();

        MvcResult provisioned = mockMvc.perform(post("/platform/v1/tenants")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"first-tenant","username":"first-user","password":"%s"}
                                """.formatted(INITIAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("first-tenant"))
                .andExpect(jsonPath("$.username").value("first-user"))
                .andReturn();

        String tenantId = JsonPath.read(provisioned.getResponse().getContentAsString(), "$.tenantId");
        String identityId = JsonPath.read(provisioned.getResponse().getContentAsString(), "$.identityId");

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM tenants").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM identities").query(Integer.class).single()).isEqualTo(1);

        MvcResult login = mockMvc.perform(post("/t/{slug}/v1/auth/login", "first-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"first-user","password":"%s"}
                                """.formatted(INITIAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/t/{slug}/v1/me", "first-tenant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(identityId))
                .andExpect(jsonPath("$.username").value("first-user"))
                .andExpect(jsonPath("$.tid").value(tenantId));
    }

    private static String basicAuth() {
        String token = Base64.getEncoder()
                .encodeToString((PLATFORM_USER + ":" + PLATFORM_PASSWORD).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
