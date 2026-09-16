package com.schwab.shortener.identity;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new AppProperties(
                new AppProperties.Jwt("test-secret-key-must-be-at-least-thirty-two-chars", "test", Duration.ofMinutes(15), Duration.ofDays(1)),
                new AppProperties.Shortener(7, Duration.ofMinutes(10), 60, false),
                new AppProperties.Orchestration(3, Duration.ofMillis(10), false),
                new AppProperties.Security(java.util.List.of())
        ));
        authService = new AuthService(users, passwordEncoder, jwtService);
    }

    @Test
    void loginIssuesTokensForValidUser() {
        AppUser user = user("engineer", "ENGINEER");
        when(users.findByUsernameIgnoreCase("engineer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Engineer@123", "hash")).thenReturn(true);

        AuthService.TokenResponse tokens = authService.login(new AuthService.LoginRequest("engineer", "Engineer@123"));
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.username()).isEqualTo("engineer");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void loginRejectsBadPasswordAndMissingUser() {
        when(users.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new AuthService.LoginRequest("missing", "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).status())
                .isEqualTo(401);

        AppUser user = user("engineer", "ENGINEER");
        when(users.findByUsernameIgnoreCase("engineer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new AuthService.LoginRequest("engineer", "wrong")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshAcceptsRefreshTokenAndRejectsAccessToken() {
        AppUser user = user("engineer", "ENGINEER");
        when(users.findByUsernameIgnoreCase("engineer")).thenReturn(Optional.of(user));

        String refresh = jwtService.issueRefreshToken(user);
        AuthService.TokenResponse refreshed = authService.refresh(new AuthService.RefreshRequest(refresh));
        assertThat(refreshed.accessToken()).isNotBlank();

        String access = jwtService.issueAccessToken(user);
        assertThatThrownBy(() -> authService.refresh(new AuthService.RefreshRequest(access)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    private static AppUser user(String username, String roles) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setRoles(roles);
        user.setEnabled(true);
        user.setPasswordHash("hash");
        return user;
    }
}
