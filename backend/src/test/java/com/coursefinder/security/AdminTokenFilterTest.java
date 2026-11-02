package com.coursefinder.security;

import com.coursefinder.config.properties.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpServletResponse run(String configuredToken, String method, String headerValue) throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter(new AdminProperties(configuredToken), objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/admin/stats");
        if (headerValue != null) {
            request.addHeader(AdminTokenFilter.HEADER, headerValue);
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
    void correctTokenPassesThrough() throws Exception {
        assertThat(run("s3cret-token", "GET", "s3cret-token").getStatus()).isEqualTo(200);
    }

    @Test
    void missingTokenIs401ProblemJson() throws Exception {
        MockHttpServletResponse response = run("s3cret-token", "POST", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("X-Admin-Token").doesNotContain("s3cret-token");
    }

    @Test
    void wrongTokenIs401() throws Exception {
        assertThat(run("s3cret-token", "GET", "s3cret-tokeN").getStatus()).isEqualTo(401);
        assertThat(run("s3cret-token", "GET", "").getStatus()).isEqualTo(401);
    }

    @Test
    void noConfiguredTokenLocksAdminCompletely() throws Exception {
        assertThat(run("", "GET", "").getStatus()).isEqualTo(401);
        assertThat(run(null, "GET", "anything").getStatus()).isEqualTo(401);
    }

    @Test
    void corsPreflightIsNotBlocked() throws Exception {
        assertThat(run("s3cret-token", "OPTIONS", null).getStatus()).isEqualTo(200);
    }
}
