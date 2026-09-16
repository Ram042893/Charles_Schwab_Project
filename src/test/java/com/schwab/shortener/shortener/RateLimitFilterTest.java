package com.schwab.shortener.shortener;

import com.schwab.shortener.config.AppProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void allowsNonCreateTrafficAndBlocksExcessiveCreates() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new AppProperties(
                new AppProperties.Jwt("test-secret-key-must-be-at-least-thirty-two-chars", "test", Duration.ofMinutes(15), Duration.ofDays(1)),
                new AppProperties.Shortener(7, Duration.ofMinutes(10), 2, false),
                new AppProperties.Orchestration(3, Duration.ofMillis(10), false),
                new AppProperties.Security(java.util.List.of())
        ));

        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/urls");
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        filter.doFilter(get, getResponse, chain);
        assertThat(getResponse.getStatus()).isEqualTo(200);
        assertThat(passed.get()).isEqualTo(1);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/urls");
            post.setRemoteAddr("10.0.0.8");
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(post, ok, chain);
            assertThat(ok.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/urls");
        blockedReq.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Rate limit exceeded");
        assertThat(passed.get()).isEqualTo(3);
    }
}
