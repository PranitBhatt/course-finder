package com.coursefinder.integration;

import com.coursefinder.config.properties.JwtProperties;
import com.coursefinder.entity.Role;
import com.coursefinder.security.JwtService;
import com.coursefinder.support.TestJwtKeys;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

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

    // A test RSA key, passed in like JWT_PRIVATE_KEY would be in production.
    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
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

        // Try to become Ada by putting her public id into the "sub" claim of Bob's token.
        // The payload is only base64, so anyone can change it - but then the signature no longer matches.
        String[] parts = bobsToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace(publicIdOf("bob@example.com"), publicIdOf("ada@example.com"));
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
    void guestSearchWithABadTokenStillWorks() throws Exception {
        // Spring would answer 401 for any bad token; on public endpoints we ignore the token instead,
        // so a user whose old token expired can still search.
        mockMvc.perform(get("/api/courses/search").param("q", "python").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void meWithTokenSignedByAnotherKeyReturns401() throws Exception {
        registerAndLogin("ada@example.com");

        // Correct claims for Ada, correct kid - but signed with a key that isn't ours.
        RSAKey otherKey = TestJwtKeys.generate(TestJwtKeys.KEY_ID);
        JwtService forger = new JwtService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey))),
                otherKey, jwtProperties);
        String forgedToken = forger.createToken(
                UUID.fromString(publicIdOf("ada@example.com")), UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void jwksEndpointPublishesOnlyThePublicKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value(TestJwtKeys.KEY_ID))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").isString())
                // "d", "p", "q" are parts of the PRIVATE key and must never be published.
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
    }

    @Test
    void adminEndpointStillWorksWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    private String publicIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT public_id::text FROM app_user WHERE email = ?", String.class, email);
    }

    /** Registers a user, logs in, and returns the access token from the login response. */
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

        return JsonPath.read(loginResponse, "$.accessToken");
    }
}
