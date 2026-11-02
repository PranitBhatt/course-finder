package com.coursefinder.security;

import com.coursefinder.config.properties.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static MockHttpServletResponse search(SearchRateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses/search");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsUpToTheLimitThenReturns429WithRetryAfter() throws Exception {
        SearchRateLimitFilter filter = new SearchRateLimitFilter(new RateLimitProperties(3), objectMapper);

        assertThat(search(filter, "1.1.1.1").getHeader(SearchRateLimitFilter.REMAINING_HEADER)).isEqualTo("2");
        assertThat(search(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(search(filter, "1.1.1.1").getHeader(SearchRateLimitFilter.REMAINING_HEADER)).isEqualTo("0");

        MockHttpServletResponse blocked = search(filter, "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        assertThat(Integer.parseInt(blocked.getHeader("Retry-After"))).isBetween(1, 60);
    }

    @Test
    void eachIpHasItsOwnBucket() throws Exception {
        SearchRateLimitFilter filter = new SearchRateLimitFilter(new RateLimitProperties(1), objectMapper);

        assertThat(search(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(search(filter, "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(search(filter, "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void zeroDisablesTheLimit() throws Exception {
        SearchRateLimitFilter filter = new SearchRateLimitFilter(new RateLimitProperties(0), objectMapper);

        for (int i = 0; i < 100; i++) {
            assertThat(search(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }
}
