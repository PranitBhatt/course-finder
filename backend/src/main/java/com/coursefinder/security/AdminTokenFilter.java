package com.coursefinder.security;

import com.coursefinder.config.properties.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards {@code /api/admin/**}: the request must carry {@code X-Admin-Token} equal to ADMIN_TOKEN,
 * otherwise 401. If no token is configured, admin endpoints are always locked.
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(AdminProperties properties, ObjectMapper objectMapper) {
        this.expectedToken = properties.hasToken() ? properties.token().getBytes(StandardCharsets.UTF_8) : null;
        this.objectMapper = objectMapper;
        if (expectedToken == null) {
            log.warn("ADMIN_TOKEN is not set - all /api/admin endpoints will return 401");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Let CORS preflight through; it never carries the token and runs no admin code.
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isAuthorized(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rejected admin request {} {} from {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());
        ProblemResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Missing or invalid " + HEADER + " header");
    }

    private boolean isAuthorized(String providedToken) {
        if (expectedToken == null || providedToken == null) {
            return false;
        }
        // Constant-time comparison, so the token can't be guessed from response timing.
        return MessageDigest.isEqual(expectedToken, providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
