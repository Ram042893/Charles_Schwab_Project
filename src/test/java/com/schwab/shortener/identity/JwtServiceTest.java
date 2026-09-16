package com.schwab.shortener.identity;

import com.schwab.shortener.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new AppProperties(
            new AppProperties.Jwt("test-secret-key-must-be-at-least-thirty-two-chars", "agentic-url-shortener", Duration.ofMinutes(15), Duration.ofDays(1)),
            new AppProperties.Shortener(7, Duration.ofMinutes(10), 60, false),
            new AppProperties.Orchestration(3, Duration.ofMillis(10), false),
            new AppProperties.Security(java.util.List.of())
    ));

    @Test
    void issuesParsableAccessAndRefreshTokens() {
        AppUser user = new AppUser();
        user.setUsername("engineer");
        user.setRoles("ENGINEER,REVIEWER");
        user.setEnabled(true);
        user.setPasswordHash("hash");

        String access = jwtService.issueAccessToken(user);
        String refresh = jwtService.issueRefreshToken(user);

        Claims accessClaims = jwtService.parse(access);
        assertThat(accessClaims.getSubject()).isEqualTo("engineer");
        assertThat(accessClaims.getIssuer()).isEqualTo("agentic-url-shortener");
        assertThat(jwtService.rolesFrom(accessClaims)).containsExactly("ENGINEER", "REVIEWER");

        Claims refreshClaims = jwtService.parse(refresh);
        assertThat(refreshClaims.get("type")).isEqualTo("refresh");
        assertThat(jwtService.rolesFrom(refreshClaims)).isEmpty();
    }
}
