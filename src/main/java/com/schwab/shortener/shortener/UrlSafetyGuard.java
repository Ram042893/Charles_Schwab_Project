package com.schwab.shortener.shortener;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.config.AppProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlSafetyGuard {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "file", "ftp");

    private final AppProperties properties;

    public UrlSafetyGuard(AppProperties properties) {
        this.properties = properties;
    }

    public URI validatePublicHttpUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "Target URL is not a valid URI");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new BusinessException(400, "Target URL must include scheme and host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (BLOCKED_SCHEMES.contains(scheme) || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new BusinessException(400, "Only http/https URLs can be shortened");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost") || "metadata.google.internal".equals(host)) {
            throw new BusinessException(400, "Private or metadata hosts are not allowed");
        }
        if (host.matches("^(10\\.|127\\.|192\\.168\\.|169\\.254\\.).*") || host.startsWith("172.")) {
            throw new BusinessException(400, "Private or non-routable destinations are not allowed");
        }
        if (properties.shortener().resolveHosts()) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new BusinessException(400, "Private or non-routable destinations are not allowed");
                }
            } catch (UnknownHostException ex) {
                throw new BusinessException(400, "Target host cannot be resolved");
            }
        }
        return uri;
    }
}
