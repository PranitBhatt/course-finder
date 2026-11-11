package com.coursefinder.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * An RSA key pair for tests, generated once per test run, so no private key is ever committed.
 * Integration tests pass it to the app exactly like JWT_PRIVATE_KEY would be passed in production.
 */
public final class TestJwtKeys {

    public static final String KEY_ID = "test-key";
    public static final RSAKey SIGNING_KEY = generate(KEY_ID);

    private TestJwtKeys() {
    }

    /** Use from a {@code @DynamicPropertySource} method in a {@code @SpringBootTest}. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.private-key", () -> base64Pem(SIGNING_KEY));
        registry.add("app.jwt.key-id", () -> KEY_ID);
    }

    public static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The private key as a PKCS#8 PEM, base64-encoded into one line - the JWT_PRIVATE_KEY format. */
    public static String base64Pem(RSAKey key) {
        try {
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(key.toPrivateKey().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
