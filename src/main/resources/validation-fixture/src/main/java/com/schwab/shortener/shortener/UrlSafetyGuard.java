package com.schwab.shortener.shortener;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Fixture copy of the production SSRF guard used by the isolated Maven validation loop.
 * Kept dependency-free so the sandbox build stays fast.
 */
public class UrlSafetyGuard {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "file", "ftp");

    public URI validatePublicHttpUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Target URL is not a valid URI");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Target URL must include scheme and host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (BLOCKED_SCHEMES.contains(scheme) || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("Only http/https URLs can be shortened");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost") || "metadata.google.internal".equals(host)) {
            throw new IllegalArgumentException("Private or metadata hosts are not allowed");
        }
        if (host.matches("^(10\\.|127\\.|192\\.168\\.|169\\.254\\.).*") || host.startsWith("172.")) {
            throw new IllegalArgumentException("Private or non-routable destinations are not allowed");
        }
        return uri;
    }
}
