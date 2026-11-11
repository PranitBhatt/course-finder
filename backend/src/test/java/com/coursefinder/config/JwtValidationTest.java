package com.coursefinder.config;

import com.coursefinder.config.properties.JwtProperties;
import com.coursefinder.entity.Role;
import com.coursefinder.security.JwtService;
import com.coursefinder.support.TestJwtKeys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JwtDecoder from JwtKeyConfig on its own: which tokens it accepts and which it refuses.
 * No Spring context or database needed.
 */
class JwtValidationTest {

    private static final String ISSUER = "course-finder-api";
    private static final String AUDIENCE = "course-finder-web";
    private static final RSAKey KEY = TestJwtKeys.SIGNING_KEY;

    private final JwtProperties properties = new JwtProperties(
            null, TestJwtKeys.KEY_ID, ISSUER, AUDIENCE, Duration.ofMinutes(15), false);
    private final JwtKeyConfig config = new JwtKeyConfig();
    private final JwtDecoder decoder = createDecoder();

    private JwtDecoder createDecoder() {
        try {
            return config.jwtDecoder(KEY, properties);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void tokenFromJwtServicePassesWithAllClaims() {
        JwtService jwtService = new JwtService(config.jwtEncoder(KEY), KEY, properties);
        UUID sessionId = UUID.randomUUID();

        Jwt jwt = decoder.decode(jwtService.createToken(7L, sessionId, Role.USER));

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", TestJwtKeys.KEY_ID);
        assertThat(jwt.getSubject()).isEqualTo("7");
        // getIssuer() would try to turn it into a URL; ours is a plain name.
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = sign(KEY, claims -> claims.issuer("someone-else"));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongAudienceIsRejected() {
        // A real token from us... but meant for a different service.
        String token = sign(KEY, claims -> claims.audience(List.of("billing-service")));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        String token = sign(KEY, claims -> claims.issuedAt(now.minusSeconds(3600)).expiresAt(now.minusSeconds(300)));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenJustExpiredWithinClockSkewStillPasses() {
        // 10 seconds over is inside the 30 second tolerance for clocks that are slightly off.
        Instant now = Instant.now();
        String token = sign(KEY, claims -> claims.issuedAt(now.minusSeconds(900)).expiresAt(now.minusSeconds(10)));
        assertThat(decoder.decode(token).getSubject()).isEqualTo("7");
    }

    @Test
    void tamperedTokenIsRejected() {
        String[] parts = sign(KEY, claims -> { }).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"sub\":\"7\"", "\"sub\":\"1\"").getBytes(StandardCharsets.UTF_8));
        assertThat(forged).isNotEqualTo(parts[1]); // make sure we really changed something

        String tampered = parts[0] + "." + forged + "." + parts[2];
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithADifferentKeyIsRejected() {
        // Same kid, same claims, but signed by a key that isn't ours.
        RSAKey attackersKey = TestJwtKeys.generate(TestJwtKeys.KEY_ID);
        String token = sign(attackersKey, claims -> { });
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenWithAlgNoneIsRejected() {
        // Classic attack: claim "no signature needed". Only RS256 is accepted.
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"sub\":\"1\",\"iss\":\"%s\",\"aud\":\"%s\",\"exp\":%d}"
                .formatted(ISSUER, AUDIENCE, Instant.now().plusSeconds(600).getEpochSecond()));
        assertThatThrownBy(() -> decoder.decode(header + "." + payload + ".")).isInstanceOf(JwtException.class);
    }

    @Test
    void keyFromBase64PemIsLoadedCorrectly() {
        RSAKey loaded = JwtKeyConfig.loadFromBase64Pem(TestJwtKeys.base64Pem(KEY), "loaded-key");

        assertThat(loaded.getKeyID()).isEqualTo("loaded-key");
        assertThat(loaded.isPrivate()).isTrue();
        assertThat(loaded.getModulus()).isEqualTo(KEY.getModulus());
    }

    @Test
    void missingKeyFailsUnlessGeneratingIsAllowed() {
        JwtProperties noKey = new JwtProperties(null, null, ISSUER, AUDIENCE, Duration.ofMinutes(15), false);
        assertThatThrownBy(() -> config.jwtSigningKey(noKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY is not set");

        JwtProperties devMode = new JwtProperties(null, null, ISSUER, AUDIENCE, Duration.ofMinutes(15), true);
        assertThat(config.jwtSigningKey(devMode).isPrivate()).isTrue();
    }

    @Test
    void pkcs1KeyGivesAHelpfulError() {
        String pkcs1 = Base64.getEncoder().encodeToString(
                "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> JwtKeyConfig.loadFromBase64Pem(pkcs1, "k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#1");
    }

    /** A token with valid default claims, signed by the given key, with some claims changed. */
    private static String sign(RSAKey key, Consumer<JwtClaimsSet.Builder> changes) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject("7")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900));
        changes.accept(claims);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)))
                .encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
