package com.coursefinder.config;

import com.coursefinder.security.JsonAccessDeniedHandler;
import com.coursefinder.security.JsonAuthenticationEntryPoint;
import com.coursefinder.security.JwtAuthenticationFilter;
import com.coursefinder.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

/**
 * Decides which endpoints need a logged-in user.
 * <p>
 * This runs after the CORS, admin token and rate limit filters (ApiSecurityConfig / CorsConfig),
 * which are registered with a higher priority, so those keep working exactly as before.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Optional<JwtService> jwtService,
                                                   ObjectMapper objectMapper) throws Exception {
        // JwtService only exists when app.features.auth.enabled=true.
        boolean authEnabled = jwtService.isPresent();

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
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .authorizeHttpRequests(auth -> {
                    // Guest search must never need a login.
                    auth.requestMatchers("/api/courses/**", "/api/sources/**", "/actuator/health/**").permitAll();
                    // Open here because AdminTokenFilter already guards these with X-Admin-Token.
                    auth.requestMatchers("/api/admin/**").permitAll();
                    // Register and login are how you get a token, so they can't require one.
                    auth.requestMatchers("/api/auth/**").permitAll();
                    // Spring forwards errors to /error; locking it would turn every error into a 401.
                    auth.requestMatchers("/error").permitAll();

                    if (authEnabled) {
                        auth.requestMatchers("/api/me").authenticated();
                    } else {
                        // With auth off the controller doesn't exist, so this gives a plain 404.
                        auth.requestMatchers("/api/me").permitAll();
                    }

                    // Anything not listed is locked, so a new endpoint is private until someone opens it on purpose.
                    auth.anyRequest().authenticated();
                });

        // Check the Bearer token before Spring's own login filter would run.
        jwtService.ifPresent(service ->
                http.addFilterBefore(new JwtAuthenticationFilter(service), UsernamePasswordAuthenticationFilter.class));

        return http.build();
    }
}
