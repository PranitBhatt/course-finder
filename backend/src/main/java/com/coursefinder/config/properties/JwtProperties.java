package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param privateKey            RSA private key for signing: a PKCS#8 PEM file, base64-encoded into one line
 *                              (JWT_PRIVATE_KEY). See .env.example for how to create it.
 * @param keyId                 name of that key, written into every token header as "kid" (JWT_KEY_ID)
 * @param issuer                "iss" claim: who created the token. Tokens with another issuer are rejected.
 * @param audience              "aud" claim: who the token is for. Tokens for another audience are rejected.
 * @param accessTokenExpiration how long an access token stays valid
 * @param generateKeyIfMissing  create a throwaway key at startup when none is configured (dev only)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String privateKey,
        String keyId,
        String issuer,
        String audience,
        Duration accessTokenExpiration,
        boolean generateKeyIfMissing
) {

    /** Never print the private key itself. */
    @Override
    public String toString() {
        return "JwtProperties[privateKey=****, keyId=%s, issuer=%s, audience=%s, accessTokenExpiration=%s]"
                .formatted(keyId, issuer, audience, accessTokenExpiration);
    }
}
