package com.coursefinder.integration;

import com.coursefinder.security.RefreshTokenCookies;
import com.coursefinder.support.TestJwtKeys;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force lock, public ids, disabled users, hash upgrades, password rules and log hygiene,
 * over real HTTP against a real PostgreSQL. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.features.auth.enabled=true",
        "app.cors.allowed-origins=" + LoginHardeningIntegrationTest.FRONTEND_ORIGIN,
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoginHardeningIntegrationTest {

    static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String EMAIL = "ada@example.com";
    private static final String PASSWORD = "correct-horse-battery";

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

    // ---------------------------------------------------------------- brute force

    @Test
    void fiveWrongPasswordsLockTheAccountFor15Minutes() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());

        for (int i = 1; i <= 4; i++) {
            login(EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(EMAIL, "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(jsonPath("$.status").value(429));

        // Locked: even the right password gets 429, with the remaining time.
        login(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // 15 minutes later (simulated), the right password works and everything is reset.
        jdbcTemplate.update("UPDATE app_user SET locked_until = now() - interval '1 second'");
        login(EMAIL, PASSWORD).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT failed_login_count FROM app_user", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT locked_until IS NULL FROM app_user", Boolean.class)).isTrue();
    }

    @Test
    void loginRecordsLastLoginAt() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE app_user SET last_login_at = NULL");

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_login_at > now() - interval '1 minute' FROM app_user", Boolean.class)).isTrue();
    }

    // ---------------------------------------------------------------- password policy

    @Test
    void registerEnforcesPasswordLength() throws Exception {
        register(EMAIL, "short7!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("must be at least 8 characters"));

        // 37 x "é" = 37 characters but 74 bytes: over BCrypt's limit.
        register(EMAIL, "é".repeat(37))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---------------------------------------------------------------- public ids

    @Test
    void apiAndTokenUsePublicIdNeverTheNumericId() throws Exception {
        String response = register(EMAIL, PASSWORD).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String publicId = jdbcTemplate.queryForObject("SELECT public_id::text FROM app_user", String.class);
        String accessToken = JsonPath.read(response, "$.accessToken");
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat((String) JsonPath.read(response, "$.user.id")).isEqualTo(publicId);
        assertThat(payload).contains("\"sub\":\"" + publicId + "\"");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicId));
    }

    @Test
    void databaseFillsInPublicIdForRowsWithoutOne() {
        // What the migration did for users that existed before: the column default creates the UUID.
        jdbcTemplate.update("""
                INSERT INTO app_user (email, password_hash, display_name, created_at)
                VALUES ('old@example.com', 'x', 'Old', now())""");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT public_id::text FROM app_user WHERE email = 'old@example.com'", String.class))
                .matches("[0-9a-f-]{36}");
    }

    // ---------------------------------------------------------------- disabled users

    @Test
    void disabledUserCannotLogInOrRefresh() throws Exception {
        MvcResult registered = register(EMAIL, PASSWORD).andExpect(status().isCreated()).andReturn();
        Cookie refreshCookie = registered.getResponse().getCookie(RefreshTokenCookies.NAME);

        jdbcTemplate.update("UPDATE app_user SET status = 'DISABLED'");

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account disabled"));
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- hash upgrade

    @Test
    void outdatedHashIsReplacedOnLogin() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        // Pretend this user signed up back when we used a weaker BCrypt cost (4 instead of 10).
        jdbcTemplate.update("UPDATE app_user SET password_hash = ?", new BCryptPasswordEncoder(4).encode(PASSWORD));

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM app_user", String.class))
                .startsWith("$2a$10$");
        login(EMAIL, PASSWORD).andExpect(status().isOk()); // the new hash works
    }

    // ---------------------------------------------------------------- logs

    @Test
    void logsNeverContainPasswordsTokensOrFullEmails(CapturedOutput output) throws Exception {
        MvcResult registered = register(EMAIL, PASSWORD).andExpect(status().isCreated()).andReturn();
        String accessToken = JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
        Cookie oldRefresh = registered.getResponse().getCookie(RefreshTokenCookies.NAME);

        // Trigger the code paths that log something: reuse detection and the account lock.
        mockMvc.perform(post("/api/auth/refresh").cookie(oldRefresh).header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE refresh_token SET used_at = now() - interval '1 minute' WHERE used_at IS NOT NULL");
        mockMvc.perform(post("/api/auth/refresh").cookie(oldRefresh).header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized());
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-guess-" + i);
        }

        assertThat(output.getAll())
                .contains("reuse detected")
                .contains("locked")
                .doesNotContain(EMAIL)
                .doesNotContain(PASSWORD)
                .doesNotContain("wrong-guess-")
                .doesNotContain(oldRefresh.getValue())
                .doesNotContain(accessToken);
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "displayName": "Ada"}
                """.formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }
}
