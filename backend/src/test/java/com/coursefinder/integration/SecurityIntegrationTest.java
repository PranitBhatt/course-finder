package com.coursefinder.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security rules end to end: /api/me needs a valid token, while guest search
 * and the X-Admin-Token admin endpoints keep working without one.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.features.auth.enabled=true",
        "app.jwt.secret=test-jwt-secret-at-least-32-bytes-long",
        "app.admin.token=" + SecurityIntegrationTest.ADMIN_TOKEN,
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    static final String ADMIN_TOKEN = "test-admin-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.execute("TRUNCATE app_user RESTART IDENTITY");
    }

    @Test
    void meWithoutTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void meWithTokenFromLoginReturnsCurrentUser() throws Exception {
        String token = registerAndLogin("ada@example.com");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ada"));
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        registerAndLogin("ada@example.com");
        String bobsToken = registerAndLogin("bob@example.com");

        // Try to become user 1 by editing the "sub" claim in Bob's token (user 2).
        // The payload is only base64, so anyone can change it - but then the signature no longer matches.
        String[] parts = bobsToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("\"sub\":\"2\"", "\"sub\":\"1\"");
        assertThat(forgedPayload).isNotEqualTo(payload); // make sure we really changed something
        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void guestSearchWithoutTokenStillWorks() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "python"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointStillWorksWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    /** Registers a user, logs in, and returns the token from the login response. */
    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret123", "displayName": "Ada"}
                        """.formatted(email)))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(loginResponse, "$.token");
    }
}
