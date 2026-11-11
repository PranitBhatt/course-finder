package com.coursefinder.config;

import com.coursefinder.security.JsonAccessDeniedHandler;
import com.coursefinder.security.JsonAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Optional;

/**
 * Decides which endpoints need a logged-in user, and how a Bearer token is checked.
 * <p>
 * This runs after the CORS, admin token, rate limit and origin check filters (ApiSecurityConfig /
 * CorsConfig), which are registered with a higher priority, so those keep working exactly as before.
 */
@Configuration
public class SecurityConfig {

    /** Endpoints anyone can call without a token. */
    private static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            // Guest search must never need a login.
            path("/api/courses/**"),
            path("/api/sources/**"),
            path("/actuator/health/**"),
            // Open here because AdminTokenFilter already guards these with X-Admin-Token.
            path("/api/admin/**"),
            // Register, login, refresh and logout are how you get a token, so they can't require one.
            path("/api/auth/**"),
            // The public key, so others can verify our tokens.
            path("/.well-known/jwks.json"),
            // Spring forwards errors to /error; locking it would turn every error into a 401.
            path("/error"));

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Optional<JwtDecoder> jwtDecoder,
                                                   ObjectMapper objectMapper) throws Exception {
        // The JwtDecoder (JwtKeyConfig) only exists when app.features.auth.enabled=true.
        boolean authEnabled = jwtDecoder.isPresent();
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(objectMapper);

        http
                // CSRF tricks a browser into sending a request with the cookies it already has.
                // The access token isn't a cookie: our own JavaScript adds it to the Authorization
                // header, which another site can't do. The one cookie we have (the refresh token) is
                // only sent to /api/auth and is protected there by SameSite=Lax plus OriginCheckFilter,
                // so Spring's token-based CSRF protection isn't needed.
                .csrf(csrf -> csrf.disable())
                // No HTTP session: every request proves who it is with its own token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Turn off Spring's built-in login page, basic auth and /logout - we have our own endpoints.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();

                    if (authEnabled) {
                        auth.requestMatchers("/api/me").authenticated();
                    } else {
                        // With auth off the controller doesn't exist, so this gives a plain 404.
                        auth.requestMatchers("/api/me").permitAll();
                    }

                    // Anything not listed is locked, so a new endpoint is private until someone opens it on purpose.
                    auth.anyRequest().authenticated();
                });

        if (authEnabled) {
            // Spring's resource server replaces our old hand-written filter: it reads the Bearer token,
            // checks it with the JwtDecoder (signature, expiry, issuer, audience) and fills the SecurityContext.
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .bearerTokenResolver(ignoreTokensOnPublicEndpoints())
                    .jwt(jwt -> jwt
                            .decoder(jwtDecoder.get())
                            .jwtAuthenticationConverter(rolesToAuthorities()))
                    // Bad token -> our ProblemDetail 401 instead of Spring's empty one.
                    .authenticationEntryPoint(entryPoint));
        }

        return http.build();
    }

    /**
     * Spring's resource server answers 401 as soon as a request carries a bad token - even on
     * endpoints anyone may call. A guest whose old token expired would then be locked out of search.
     * So on public endpoints we pretend no token was sent; they work the same for everyone.
     */
    private static BearerTokenResolver ignoreTokensOnPublicEndpoints() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : defaultResolver.resolve(request);
    }

    /**
     * Turns the token's "roles" claim (e.g. ["USER"]) into Spring authorities (ROLE_USER), so later
     * we can write rules like {@code hasRole("ADMIN")}. By default Spring would look at a "scope" claim.
     */
    private static JwtAuthenticationConverter rolesToAuthorities() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static RequestMatcher path(String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(pattern);
    }
}
