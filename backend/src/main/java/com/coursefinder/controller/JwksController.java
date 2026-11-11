package com.coursefinder.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /.well-known/jwks.json} - our PUBLIC key in the standard JWK Set format.
 * <p>
 * Another service can download this and verify our tokens itself (matching each token's "kid"),
 * without being able to create tokens. Safe to publish: it never contains the private key.
 */
@RestController
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey jwtSigningKey) {
        // toPublicJWK() drops every private part of the key before anything is published.
        this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
