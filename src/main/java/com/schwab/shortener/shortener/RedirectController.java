package com.schwab.shortener.shortener;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        UrlShortenerService.CachedTarget target = service.resolveActive(code);
        service.recordClick(code, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target.targetUrl())
                .build();
    }
}
