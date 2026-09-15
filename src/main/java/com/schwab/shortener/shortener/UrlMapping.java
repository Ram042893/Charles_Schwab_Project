package com.schwab.shortener.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "url_mappings", indexes = {
        @Index(name = "idx_url_code", columnList = "code", unique = true),
        @Index(name = "idx_url_owner", columnList = "ownerUsername")
})
public class UrlMapping {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 2048)
    private String targetUrl;

    @Column(nullable = false, length = 80)
    private String ownerUsername;

    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private long clickCount;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
