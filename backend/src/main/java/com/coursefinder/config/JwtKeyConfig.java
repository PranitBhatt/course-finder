package com.coursefinder.config;

import com.coursefinder.config.properties.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * The RSA key pair for access tokens, and the encoder/decoder built from it.
 * <p>
 * RS256 is asymmetric: the PRIVATE key signs, the PUBLIC key verifies. Only this backend has the
 * private key, but the public key can be shared freely (see /.well-known/jwks.json) - anyone can
 * check our tokens, nobody else can create them. With the old HS256 secret, whoever could verify
 * could also sign.
 * <p>
 * Only exists when auth is enabled, so prod doesn't need a key until auth is switched on there.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    // Allows for small clock differences between servers when checking exp/iat.
    // Keep it short: every second here is a second longer a token stays usable.
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public RSAKey jwtSigningKey(JwtProperties properties) {
        if (StringUtils.hasText(properties.privateKey())) {
            if (!StringUtils.hasText(properties.keyId())) {
                throw new IllegalStateException("JWT_KEY_ID must be set together with JWT_PRIVATE_KEY");
            }
            return loadFromBase64Pem(properties.privateKey(), properties.keyId());
        }

        if (properties.generateKeyIfMissing()) {
            // A new key on every start means every access token from before the restart becomes invalid.
            // Fine locally (the frontend just refreshes); never acceptable with real users.
            log.warn("JWT_PRIVATE_KEY is not set - generating a temporary RSA key. Only acceptable for local development.");
            return generateKey("dev-generated");
        }

        // Fail at startup rather than run without a way to sign or check tokens.
        throw new IllegalStateException("JWT_PRIVATE_KEY is not set. See .env.example for how to create one.");
    }

    /** Signs tokens with the private key. The "kid" in the token header picks the key from this set. */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * Checks incoming tokens using only the PUBLIC key. A token passes only if all of these hold:
     * <ul>
     *   <li>signature made with our private key, using RS256 (any other algorithm is refused)</li>
     *   <li>not expired, and not issued in the future (with {@link #CLOCK_SKEW} tolerance)</li>
     *   <li>"iss" is us - stops tokens from some other system that happens to use the same key format</li>
     *   <li>"aud" includes our audience - stops a token meant for another service from being used here</li>
     * </ul>
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Reads the key from JWT_PRIVATE_KEY: base64 of a PEM file like
     * {@code -----BEGIN PRIVATE KEY----- ... -----END PRIVATE KEY-----}. It's base64-encoded once more
     * because a multi-line PEM is awkward in environment variables.
     */
    static RSAKey loadFromBase64Pem(String base64Pem, String keyId) {
        String pem = new String(Base64.getDecoder().decode(base64Pem.trim()), StandardCharsets.UTF_8);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            // Older "openssl genrsa" writes PKCS#1, which Java can't read directly.
            throw new IllegalStateException("JWT_PRIVATE_KEY is in PKCS#1 format (BEGIN RSA PRIVATE KEY). "
                    + "Convert it with: openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem");
        }
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            // The public key is two numbers (modulus + public exponent) that are also stored inside
            // the private key, so we don't need a second environment variable for it.
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException | ClassCastException | IllegalArgumentException e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid RSA private key in PKCS#8 PEM format", e);
        }
    }

    static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate an RSA key", e);
        }
    }
}
