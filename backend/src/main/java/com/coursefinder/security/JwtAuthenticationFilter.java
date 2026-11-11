package com.coursefinder.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>} and, if the token is valid, marks the request
 * as coming from that user. It never rejects a request itself; SecurityConfig decides which
 * endpoints need a logged-in user.
 * <p>
 * Not a @Component on purpose: Spring Boot registers every Filter bean for all requests,
 * so it would run once there and once again inside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // No token: carry on as a guest. Public endpoints like search must keep working.
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Long userId = jwtService.parseUserId(token);
            // The principal is just the user id. Empty authorities list = no roles yet.
            // This 3-argument constructor marks the authentication as already verified.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            // Bad, changed or expired token: treat the request as a guest instead of failing it.
            // A guest with a stale token can still search; protected endpoints still return 401.
            log.debug("Ignoring invalid JWT: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
