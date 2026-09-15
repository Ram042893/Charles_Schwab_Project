package com.schwab.shortener.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.schwab.shortener.config.AppProperties;

@Service
public class JwtService {

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            secret = Arrays.copyOf(secret, 32);
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(properties.jwt().issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().accessTokenTtl())))
                .claim("roles", user.getRoles())
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(properties.jwt().issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().refreshTokenTtl())))
                .claim("type", "refresh")
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public List<String> rolesFrom(Claims claims) {
        Object raw = claims.get("roles");
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(String.valueOf(raw).split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
