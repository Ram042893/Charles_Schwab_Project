package com.schwab.shortener.identity;

import com.schwab.shortener.common.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.username())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new BusinessException(401, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "Invalid credentials");
        }
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        try {
            Claims claims = jwtService.parse(request.refreshToken());
            if (!"refresh".equals(claims.get("type"))) {
                throw new BusinessException(401, "Invalid refresh token");
            }
            AppUser user = users.findByUsernameIgnoreCase(claims.getSubject())
                    .filter(AppUser::isEnabled)
                    .orElseThrow(() -> new BusinessException(401, "Invalid refresh token"));
            return tokensFor(user);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(401, "Invalid refresh token");
        }
    }

    private TokenResponse tokensFor(AppUser user) {
        return new TokenResponse(jwtService.issueAccessToken(user), jwtService.issueRefreshToken(user), "Bearer", user.getUsername(), user.getRoles());
    }

    public record LoginRequest(String username, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, String username, String roles) {}
}
