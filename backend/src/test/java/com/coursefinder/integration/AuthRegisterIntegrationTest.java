package com.coursefinder.integration;

import com.coursefinder.support.TestJwtKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Register and login against a real PostgreSQL with the real Flyway migrations,
 * so the UNIQUE and lowercase constraints are the same as in production.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        // The flag is off by default; the controller only exists when it is on.
        "app.features.auth.enabled=true",
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
class AuthRegisterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // A test RSA key, passed in like JWT_PRIVATE_KEY would be in production.
    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
    }

    @Test
    void registerCreatesUserAndRejectsDuplicateEmail() throws Exception {
        register("ada@example.com", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Ada"))
                .andExpect(jsonPath("$.user.createdAt").exists())
                // The response must never leak the password or its hash.
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        register("ada@example.com", "secret123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void mixedCaseEmailIsTreatedAsTheSameAccount() throws Exception {
        register("  Ada@Example.COM ", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"));

        register("ada@example.com", "secret123")
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithCorrectPasswordReturnsUser() throws Exception {
        register("ada@example.com", "secret123").andExpect(status().isCreated());

        // Different case than at registration: login normalizes the email too.
        login("ADA@example.com", "secret123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Ada"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        register("ada@example.com", "secret123").andExpect(status().isCreated());

        login("ada@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void loginWithUnknownEmailReturnsTheSame401() throws Exception {
        login("nobody@example.com", "secret123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                // Same message as a wrong password, so it can't be used to find registered emails.
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    private ResultActions register(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s", "displayName": "Ada"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
