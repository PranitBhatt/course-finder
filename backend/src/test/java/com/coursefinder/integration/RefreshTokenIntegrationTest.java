package com.coursefinder.integration;

import com.coursefinder.security.RefreshTokenCookies;
import com.coursefinder.security.TokenHasher;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh token cookie, refresh, logout and the Origin check, against a real PostgreSQL
 * with the real Flyway migrations. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.features.auth.enabled=true",
        "app.jwt.secret=test-jwt-secret-at-least-32-bytes-long",
        "app.cors.allowed-origins=" + RefreshTokenIntegrationTest.FRONTEND_ORIGIN,
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {

    static final String FRONTEND_ORIGIN = "http://localhost:5173";

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
    void loginSetsRefreshTokenCookieWithTheRightFlags() throws Exception {
        register();

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                // The refresh token must not be in the JSON anymore, only in the cookie.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(RefreshTokenCookies.NAME + "="),
                        containsString("HttpOnly"),
                        containsString("Secure"),      // default is true; only the dev profile turns it off
                        containsString("SameSite=Lax"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=604800")))); // 7 days in seconds
    }

    @Test
    void refreshWithCookieReturnsNewAccessTokenThatWorks() throws Exception {
        Cookie refreshCookie = register();

        String response = refresh(refreshCookie, FRONTEND_ORIGIN)
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
        refresh(refreshCookie, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void refreshFromForeignOriginReturns403() throws Exception {
        Cookie refreshCookie = register();

        // What a CSRF attempt from another website would look like: valid cookie, wrong Origin.
        // Only the status is checked: CorsFilter runs first and already answers 403 (plain text);
        // OriginCheckFilter is the backup layer, tested on its own in OriginCheckFilterTest.
        refresh(refreshCookie, "https://evil.example")
                .andExpect(status().isForbidden());

        // And the token was not used: it still works from our own frontend.
        refresh(refreshCookie, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void databaseStoresOnlyTheHashNeverTheRawToken() throws Exception {
        String refreshToken = register().getValue();

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
        Cookie refreshCookie = register();
        // Pretend 7 days have passed.
        jdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                TokenHasher.hash(refreshCookie.getValue()));

        refresh(refreshCookie, FRONTEND_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid refresh token"));
    }

    @Test
    void logoutClearsCookieAndRefreshThenReturns401() throws Exception {
        Cookie refreshCookie = register();

        logout(refreshCookie)
                .andExpect(status().isNoContent())
                // Max-Age=0 tells the browser to delete the cookie.
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh(refreshCookie, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());

        // Logging out again is not an error: the result is the same, the token is gone.
        logout(refreshCookie).andExpect(status().isNoContent());
    }

    @Test
    void guestSearchStillWorksWithoutAnyCookieOrToken() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "python"))
                .andExpect(status().isOk());
    }

    /** Registers Ada and returns the refresh token cookie the server set. */
    private Cookie register() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "ada@example.com", "password": "secret123", "displayName": "Ada"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "ada@example.com", "password": "secret123"}
                """));
    }

    // A browser sends the cookie by itself; in tests we attach it by hand.
    private ResultActions refresh(Cookie refreshCookie, String origin) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).header("Origin", origin));
    }

    private ResultActions logout(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie).header("Origin", FRONTEND_ORIGIN));
    }
}
