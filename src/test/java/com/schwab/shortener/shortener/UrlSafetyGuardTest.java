package com.schwab.shortener.shortener;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyGuardTest {

    private final UrlSafetyGuard guard = new UrlSafetyGuard(new AppProperties(
            new AppProperties.Jwt("test-secret-key-must-be-at-least-thirty-two-chars", "test", Duration.ofMinutes(15), Duration.ofDays(1)),
            new AppProperties.Shortener(7, Duration.ofMinutes(10), 60, false),
            new AppProperties.Orchestration(3, Duration.ofMillis(10), false),
            new AppProperties.Security(java.util.List.of())
    ));

    @Test
    void acceptsPublicHttpsUrl() {
        assertEquals("example.com", guard.validatePublicHttpUrl("https://example.com/a").getHost());
    }

    @Test
    void rejectsJavascript() {
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.validatePublicHttpUrl("javascript:alert(1)"));
        assertEquals(400, ex.status());
    }

    @Test
    void rejectsLoopback() {
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.validatePublicHttpUrl("http://127.0.0.1/secret"));
        assertTrue(ex.getMessage().toLowerCase().contains("private") || ex.getMessage().toLowerCase().contains("loop"));
    }
}
