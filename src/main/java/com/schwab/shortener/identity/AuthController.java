package com.schwab.shortener.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive JWT tokens")
    public ResponseEntity<AuthService.TokenResponse> login(@Valid @RequestBody LoginPayload payload) {
        return ResponseEntity.ok(authService.login(new AuthService.LoginRequest(payload.username(), payload.password())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate access token using a refresh token")
    public ResponseEntity<AuthService.TokenResponse> refresh(@Valid @RequestBody RefreshPayload payload) {
        return ResponseEntity.ok(authService.refresh(new AuthService.RefreshRequest(payload.refreshToken())));
    }

    public record LoginPayload(@NotBlank String username, @NotBlank String password) {}

    public record RefreshPayload(@NotBlank String refreshToken) {}
}
