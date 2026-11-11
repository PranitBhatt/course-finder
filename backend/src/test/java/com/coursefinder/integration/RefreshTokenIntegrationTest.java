package com.coursefinder.integration;

import com.coursefinder.security.TokenHasher;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh and logout against a real PostgreSQL with the real Flyway migrations.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.features.auth.enabled=true",
        "app.jwt.secret=test-jwt-secret-at-least-32-bytes-long",
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUsers() {
        // CASCADE also empties refresh_token, which references app_user.
        jdbcTemplate.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
    }

    @Test
    void refreshReturnsNewAccessTokenThatWorks() throws Exception {
        String refreshToken = registerAndGetRefreshToken();

        String response = refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(response, "$.accessToken");

        // The new access token is accepted by a protected endpoint.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        // No rotation: the same refresh token keeps working.
        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    void databaseStoresOnlyTheHashNeverTheRawToken() throws Exception {
        String refreshToken = registerAndGetRefreshToken();

        // The stored value is the SHA-256 of what the client got, not the token itself.
        String storedHash = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_token", String.class);
        assertThat(storedHash).hasSize(64).isEqualTo(TokenHasher.hash(refreshToken)).isNotEqualTo(refreshToken);

        // Stronger check: turn every row into text (all columns) and search it for the raw token.
        Integer rowsContainingRawToken = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token r WHERE strpos(r::text, ?) > 0", Integer.class, refreshToken);
        assertThat(rowsContainingRawToken).isZero();
    }

    @Test
    void expiredRefreshTokenReturns401() throws Exception {
        String refreshToken = registerAndGetRefreshToken();
        // Pretend 7 days have passed.
        jdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                TokenHasher.hash(refreshToken));

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void refreshAfterLogoutReturns401() throws Exception {
        String refreshToken = registerAndGetRefreshToken();

        logout(refreshToken).andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());

        // Logging out again is not an error: the result is the same, the token is gone.
        logout(refreshToken).andExpect(status().isNoContent());
    }

    private String registerAndGetRefreshToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "ada@example.com", "password": "secret123", "displayName": "Ada"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.refreshToken");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
    }
}
