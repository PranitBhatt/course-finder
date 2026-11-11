package com.coursefinder.security;

import com.coursefinder.config.properties.CorsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter on its own. In the running app CorsFilter usually rejects foreign origins first,
 * so this is the only place that proves the origin check itself works.
 */
class OriginCheckFilterTest {

    private static final String FRONTEND = "http://localhost:5173";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpServletResponse run(String origin) throws Exception {
        OriginCheckFilter filter = new OriginCheckFilter(new CorsProperties(List.of(FRONTEND)), objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Reaching the controller = the chain was called.
        if (chain.getRequest() != null) {
            response.setStatus(200);
        }
        return response;
    }

    @Test
    void ownFrontendPassesThrough() throws Exception {
        assertThat(run(FRONTEND).getStatus()).isEqualTo(200);
    }

    @Test
    void missingOriginPassesThrough() throws Exception {
        // Non-browser clients (curl) send no Origin; CSRF needs a browser, which always sends it cross-site.
        assertThat(run(null).getStatus()).isEqualTo(200);
    }

    @Test
    void foreignOriginIs403ProblemJson() throws Exception {
        MockHttpServletResponse response = run("https://evil.example");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("Request origin is not allowed");
    }

    @Test
    void nullOriginIsRejected() throws Exception {
        // Sandboxed iframes and some redirects send the literal string "null"; it's never our frontend.
        assertThat(run("null").getStatus()).isEqualTo(403);
    }
}
