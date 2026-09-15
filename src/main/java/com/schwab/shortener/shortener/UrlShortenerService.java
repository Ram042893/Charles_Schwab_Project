package com.schwab.shortener.shortener;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.config.CacheConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class UrlShortenerService {

    private final UrlMappingRepository urls;
    private final ClickEventRepository clicks;
    private final FeatureFlagService featureFlags;
    private final UrlSafetyGuard safetyGuard;
    private final ShortCodeGenerator codes;

    public UrlShortenerService(
            UrlMappingRepository urls,
            ClickEventRepository clicks,
            FeatureFlagService featureFlags,
            UrlSafetyGuard safetyGuard,
            ShortCodeGenerator codes
    ) {
        this.urls = urls;
        this.clicks = clicks;
        this.featureFlags = featureFlags;
        this.safetyGuard = safetyGuard;
        this.codes = codes;
    }

    @Transactional
    public UrlView create(String owner, CreateUrlRequest request) {
        safetyGuard.validatePublicHttpUrl(request.targetUrl());
        String code = resolveCode(request.customAlias());
        if (request.expiresAt() != null && !featureFlags.isEnabled(FeatureFlagService.EXPIRATION)) {
            throw new BusinessException(409, "Link expiration is not enabled");
        }
        UrlMapping mapping = new UrlMapping();
        mapping.setCode(code);
        mapping.setTargetUrl(request.targetUrl());
        mapping.setOwnerUsername(owner);
        mapping.setExpiresAt(request.expiresAt());
        mapping.setActive(true);
        mapping.setClickCount(0);
        return UrlView.from(urls.save(mapping));
    }

    @Transactional(readOnly = true)
    public List<UrlView> listMine(String owner) {
        return urls.findByOwnerUsernameOrderByCreatedAtDesc(owner).stream().map(UrlView::from).toList();
    }

    @Transactional(readOnly = true)
    public UrlView getOwned(String owner, String code) {
        UrlMapping mapping = requireOwned(owner, code);
        return UrlView.from(mapping);
    }

    @Cacheable(cacheNames = CacheConfig.URL_MAPPINGS, key = "#code")
    @Transactional(readOnly = true)
    public CachedTarget resolveActive(String code) {
        UrlMapping mapping = urls.findByCode(code).orElseThrow(() -> new BusinessException(404, "Short URL not found"));
        if (!mapping.isActive()) {
            throw new BusinessException(410, "Short URL is inactive");
        }
        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(410, "Short URL has expired");
        }
        return new CachedTarget(mapping.getTargetUrl(), mapping.getExpiresAt());
    }

    @Transactional
    public void recordClick(String code, HttpServletRequest request) {
        urls.incrementClickCount(code);
        ClickEvent event = new ClickEvent();
        event.setCode(code);
        event.setHashedIp(hashIp(request.getRemoteAddr()));
        event.setUserAgent(trim(request.getHeader("User-Agent"), 160));
        event.setReferrer(trim(request.getHeader("Referer"), 255));
        clicks.save(event);
    }

    @CacheEvict(cacheNames = CacheConfig.URL_MAPPINGS, key = "#code")
    @Transactional
    public void deactivate(String owner, String code) {
        UrlMapping mapping = requireOwned(owner, code);
        mapping.setActive(false);
        urls.save(mapping);
    }

    @Transactional(readOnly = true)
    public AnalyticsView analytics(String owner, String code) {
        UrlMapping mapping = requireOwned(owner, code);
        List<ClickEvent> events = clicks.findByCodeOrderByClickedAtDesc(code);
        return new AnalyticsView(mapping.getCode(), mapping.getTargetUrl(), mapping.getClickCount(), events.size(),
                events.stream().limit(20).map(event -> new ClickView(event.getClickedAt(), event.getReferrer())).toList());
    }

    @Transactional(readOnly = true)
    public String exportAnalytics(String owner, String code) {
        if (!featureFlags.isEnabled(FeatureFlagService.ANALYTICS_EXPORT)) {
            throw new BusinessException(409, "Analytics export is not enabled");
        }
        AnalyticsView view = analytics(owner, code);
        StringBuilder csv = new StringBuilder("code,targetUrl,clickCount,sampledClicks\n");
        csv.append(view.code()).append(',').append(view.targetUrl()).append(',').append(view.clickCount())
                .append(',').append(view.recentClicks().size()).append('\n');
        return csv.toString();
    }

    private String resolveCode(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            String generated;
            do {
                generated = codes.next();
            } while (urls.existsByCode(generated));
            return generated;
        }
        if (!featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)) {
            throw new BusinessException(409, "Custom aliases are not enabled");
        }
        String alias = customAlias.trim();
        if (!alias.matches("[A-Za-z0-9_-]{4,40}")) {
            throw new BusinessException(400, "Custom alias must be 4-40 letters, digits, underscores or hyphens");
        }
        if (urls.existsByCode(alias)) {
            throw new BusinessException(409, "Custom alias is already in use");
        }
        return alias;
    }

    private UrlMapping requireOwned(String owner, String code) {
        UrlMapping mapping = urls.findByCode(code).orElseThrow(() -> new BusinessException(404, "Short URL not found"));
        if (!mapping.getOwnerUsername().equals(owner)) {
            throw new BusinessException(403, "You do not own this short URL");
        }
        return mapping;
    }

    private String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unknown";
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record CreateUrlRequest(String targetUrl, String customAlias, Instant expiresAt) {}

    public record CachedTarget(String targetUrl, Instant expiresAt) implements java.io.Serializable {}

    public record UrlView(String code, String targetUrl, String ownerUsername, Instant expiresAt, boolean active, long clickCount, Instant createdAt) {
        static UrlView from(UrlMapping mapping) {
            return new UrlView(mapping.getCode(), mapping.getTargetUrl(), mapping.getOwnerUsername(), mapping.getExpiresAt(),
                    mapping.isActive(), mapping.getClickCount(), mapping.getCreatedAt());
        }
    }

    public record ClickView(Instant clickedAt, String referrer) {}

    public record AnalyticsView(String code, String targetUrl, long clickCount, long storedEvents, List<ClickView> recentClicks) {}
}
