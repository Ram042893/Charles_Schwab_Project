package com.schwab.shortener.shortener;

import com.schwab.shortener.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/urls") || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr();
        Window window = windows.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || Duration.between(current.startedAt, now).toMinutes() >= 1) {
                return new Window(now, new AtomicInteger(1));
            }
            current.count.incrementAndGet();
            return current;
        });
        if (window.count.get() > properties.shortener().rateLimitPerMinute()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Window(Instant startedAt, AtomicInteger count) {}
}
