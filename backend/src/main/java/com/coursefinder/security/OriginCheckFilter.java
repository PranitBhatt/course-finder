package com.coursefinder.security;

import com.coursefinder.config.properties.CorsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * CSRF check for the endpoints that act on the refresh token cookie ({@code /api/auth/refresh}
 * and {@code /api/auth/logout}): if the browser says the request comes from another site, reject it.
 * <p>
 * SameSite=Lax already stops other sites from sending our cookie in most cases. We still check,
 * because Lax isn't the whole story:
 * <ul>
 *   <li>"Same site" isn't "same origin": any subdomain of the same domain (e.g. blog.example.com
 *       next to api.example.com) counts as the same site and gets the cookie.</li>
 *   <li>Old browsers ignore SameSite completely.</li>
 *   <li>If the frontend and backend ever live on different sites, the cookie needs SameSite=None,
 *       and then this check is the only thing left.</li>
 * </ul>
 * Browsers always add an {@code Origin} header to cross-site POSTs and pages can't fake it,
 * so comparing it with our allowed origins is a reliable second layer.
 * <p>
 * Today Spring's CorsFilter (CorsConfig) runs first and happens to reject unknown origins on
 * {@code /api/**} too. That is a side effect of how Spring implements CORS, not what CORS is for
 * (CORS decides what a browser may <i>read</i>, not what the server accepts). This filter states the
 * CSRF rule on purpose, so it keeps working if the CORS setup changes.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);

    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public OriginCheckFilter(CorsProperties corsProperties, ObjectMapper objectMapper) {
        // Same list as CORS: the origins of our own frontend.
        this.allowedOrigins = corsProperties.allowedOrigins();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");

        // No Origin header: not a cross-site browser request (e.g. curl, or a same-origin request
        // from an older browser). CSRF needs a victim's browser, and browsers send Origin on cross-site POSTs.
        if (origin == null || allowedOrigins.contains(origin)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rejected {} {} from foreign origin {}", request.getMethod(), request.getRequestURI(), origin);
        ProblemResponses.write(response, objectMapper, HttpStatus.FORBIDDEN, "Forbidden",
                "Request origin is not allowed");
    }
}
