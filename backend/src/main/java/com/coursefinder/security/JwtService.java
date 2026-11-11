package com.coursefinder.security;

import com.coursefinder.config.properties.JwtProperties;
import com.coursefinder.entity.Role;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates access tokens. Checking them is Spring Security's job now (JwtDecoder in JwtKeyConfig,
 * wired into SecurityConfig) - we only write the tokens.
 * <p>
 * Only exists when auth is enabled (it needs the key from JwtKeyConfig).
 */
@Service
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final String keyId;

    public JwtService(JwtEncoder jwtEncoder, RSAKey jwtSigningKey, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.keyId = jwtSigningKey.getKeyID();
    }

    public String createToken(Long userId, UUID sessionId, Role role) {
        Instant now = Instant.now();

        // "kid" (key id) tells the verifier which key signed this token. That makes it possible to
        // switch to a new key later while tokens signed with the old one are still around.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                // "iss" = who made the token, "aud" = who it's for. Both are checked when it comes back.
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                // "sub" (subject) = who the token is about. The id never changes, unlike the email.
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenExpiration()))
                // "jti" = a unique id for this one token, so a specific token can be told apart
                // from every other one (e.g. in logs, or to block it later).
                .id(UUID.randomUUID().toString())
                // "sid" = which login session issued it, so a request can be tied to a session.
                .claim("sid", sessionId.toString())
                // A list, because a user may have several roles one day. SecurityConfig turns
                // ["USER"] into the Spring authority ROLE_USER.
                .claim("roles", List.of(role.name()))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** How long a new access token is valid; sent to the client as "expiresIn". */
    public Duration getExpiration() {
        return properties.accessTokenExpiration();
    }
}
