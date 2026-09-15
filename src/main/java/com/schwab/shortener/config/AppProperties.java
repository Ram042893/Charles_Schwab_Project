package com.schwab.shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Shortener shortener,
        Orchestration orchestration,
        Security security
) {
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Shortener(int codeLength, Duration cacheTtl, int rateLimitPerMinute, boolean resolveHosts) {}

    public record Orchestration(int maxRetries, Duration retryBackoff, boolean autoApproveLowRisk) {}

    public record Security(List<DemoUser> demoUsers) {
        public Security {
            demoUsers = demoUsers == null ? List.of() : List.copyOf(demoUsers);
        }
    }

    public record DemoUser(String username, String password, String roles) {}

    public AppProperties {
        jwt = jwt == null ? new Jwt("change-me-change-me-change-me-change-me", "agentic-url-shortener", Duration.ofMinutes(15), Duration.ofDays(7)) : jwt;
        shortener = shortener == null ? new Shortener(7, Duration.ofMinutes(10), 60, true) : shortener;
        orchestration = orchestration == null ? new Orchestration(3, Duration.ofMillis(200), false) : orchestration;
        security = security == null ? new Security(new ArrayList<>()) : security;
    }
}
