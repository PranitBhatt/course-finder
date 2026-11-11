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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions, rotating refresh token cookies, reuse detection, logout and the Origin check, against a real PostgreSQL
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

        // The access token says which session it belongs to.
        String payload = new String(Base64.getUrlDecoder().decode(newAccessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        String sessionId = jdbcTemplate.queryForObject("SELECT id::text FROM user_session", String.class);
        assertThat(payload).contains("\"sid\":\"" + sessionId + "\"");
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        Cookie first = register();

        Cookie second = refreshAndGetNewCookie(first);
        Cookie third = refreshAndGetNewCookie(second);

        // Every refresh hands out a different token.
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        assertThat(third.getValue()).isNotEqualTo(second.getValue());

        // All of them belong to the one session created at login, and only the newest is unused.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_session", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE used_at IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void replayingAnOldTokenRevokesTheWholeSession() throws Exception {
        Cookie stolenCopy = register();
        // The real user refreshes; their cookie now holds the new token.
        Cookie usersNewToken = refreshAndGetNewCookie(stolenCopy);
        // Move past the grace window: pretend the old token was used a minute ago.
        jdbcTemplate.update("UPDATE refresh_token SET used_at = now() - interval '1 minute' WHERE used_at IS NOT NULL");

        // The attacker replays the old (already used) token.
        refresh(stolenCopy, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());

        // The whole session is revoked, so even the real user's newest token stops working.
        refresh(usersNewToken, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("SELECT revoke_reason FROM user_session", String.class))
                .isEqualTo("REUSE_DETECTED");
    }

    @Test
    void reuseInsideTheGraceWindowDoesNotRevokeTheSession() throws Exception {
        Cookie oldToken = register();
        Cookie newToken = refreshAndGetNewCookie(oldToken);

        // The old token again, right away - like a second tab whose request arrived a moment later.
        refresh(oldToken, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());

        // Treated as a harmless race: the session is still alive and the new token still works.
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_at IS NULL FROM user_session", Boolean.class)).isTrue();
        refresh(newToken, FRONTEND_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void parallelRefreshesWithTheSameTokenLetExactlyOneWin() throws Exception {
        register();

        // Several rounds, each with a fresh session, to give the two requests a real chance to overlap.
        // Whatever the timing, the result must be the same: one 200, one 401, one new token.
        for (int round = 0; round < 5; round++) {
            Cookie sharedToken = loginAndGetCookie();
            CountDownLatch startSignal = new CountDownLatch(1);
            ExecutorService threads = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> statuses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    statuses.add(threads.submit(() -> {
                        startSignal.await(); // both threads wait here...
                        return refresh(sharedToken, FRONTEND_ORIGIN).andReturn().getResponse().getStatus();
                    }));
                }
                startSignal.countDown(); // ...and are released at the same moment

                assertThat(List.of(statuses.get(0).get(10, TimeUnit.SECONDS), statuses.get(1).get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(200, 401);
            } finally {
                threads.shutdown();
            }
        }

        // The loser fell inside the grace window, so no session was revoked...
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_session WHERE revoked_at IS NOT NULL", Integer.class)).isZero();
        // ...and every session (1 from register + 5 logins) has exactly one usable token, not two.
        assertThat(jdbcTemplate.queryForList(
                "SELECT count(*) FROM refresh_token WHERE used_at IS NULL GROUP BY session_id", Integer.class))
                .hasSize(6)
                .containsOnly(1);
    }

    @Test
    void sessionCannotOutliveItsAbsoluteLifetime() throws Exception {
        Cookie cookie = register();

        // Login sets the hard limit to 30 days.
        Integer days = jdbcTemplate.queryForObject(
                "SELECT round(extract(epoch FROM absolute_expires_at - created_at) / 86400)::int FROM user_session",
                Integer.class);
        assertThat(days).isEqualTo(30);

        // Pretend the session is in its last hour: a new token must not be valid longer than the session.
        jdbcTemplate.update("UPDATE user_session SET absolute_expires_at = now() + interval '1 hour'");
        MvcResult result = refresh(cookie, FRONTEND_ORIGIN).andExpect(status().isOk()).andReturn();
        Cookie lastToken = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(lastToken.getMaxAge()).isBetween(1, 3600);
        Boolean cappedToSession = jdbcTemplate.queryForObject("""
                SELECT r.expires_at <= s.absolute_expires_at
                FROM refresh_token r JOIN user_session s ON s.id = r.session_id
                WHERE r.used_at IS NULL""", Boolean.class);
        assertThat(cappedToSession).isTrue();

        // Once the 30 days are over, even an unused, unexpired token is refused.
        jdbcTemplate.update("UPDATE user_session SET absolute_expires_at = now() - interval '1 minute'");
        refresh(lastToken, FRONTEND_ORIGIN).andExpect(status().isUnauthorized());
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

    /** Refreshes with the given cookie and returns the new refresh token cookie from the response. */
    private Cookie refreshAndGetNewCookie(Cookie refreshCookie) throws Exception {
        MvcResult result = refresh(refreshCookie, FRONTEND_ORIGIN).andExpect(status().isOk()).andReturn();
        Cookie newCookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(newCookie).isNotNull();
        return newCookie;
    }

    /** Logs Ada in (a new session) and returns its refresh token cookie. */
    private Cookie loginAndGetCookie() throws Exception {
        Cookie cookie = login().andExpect(status().isOk()).andReturn().getResponse().getCookie(RefreshTokenCookies.NAME);
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
