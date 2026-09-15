package com.schwab.shortener.shortener;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "URL Shortener")
public class UrlController {

    private final UrlShortenerService service;

    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a short URL")
    public ResponseEntity<UrlShortenerService.UrlView> create(
            Authentication authentication,
            @Valid @RequestBody CreatePayload payload
    ) {
        UrlShortenerService.UrlView created = service.create(
                authentication.getName(),
                new UrlShortenerService.CreateUrlRequest(payload.targetUrl(), payload.customAlias(), payload.expiresAt())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<UrlShortenerService.UrlView> mine(Authentication authentication) {
        return service.listMine(authentication.getName());
    }

    @GetMapping("/{code}")
    public UrlShortenerService.UrlView get(Authentication authentication, @PathVariable String code) {
        return service.getOwned(authentication.getName(), code);
    }

    @GetMapping("/{code}/analytics")
    public UrlShortenerService.AnalyticsView analytics(Authentication authentication, @PathVariable String code) {
        return service.analytics(authentication.getName(), code);
    }

    @GetMapping(value = "/{code}/analytics/export", produces = "text/csv")
    public ResponseEntity<String> export(Authentication authentication, @PathVariable String code) {
        return ResponseEntity.ok(service.exportAnalytics(authentication.getName(), code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deactivate(Authentication authentication, @PathVariable String code) {
        service.deactivate(authentication.getName(), code);
        return ResponseEntity.noContent().build();
    }

    public record CreatePayload(@NotBlank String targetUrl, String customAlias, Instant expiresAt) {}
}
