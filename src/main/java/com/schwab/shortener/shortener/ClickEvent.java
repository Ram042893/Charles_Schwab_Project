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
@Table(name = "click_events", indexes = @Index(name = "idx_click_code", columnList = "code"))
public class ClickEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(length = 64)
    private String hashedIp;

    @Column(length = 160)
    private String userAgent;

    @Column(length = 255)
    private String referrer;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (clickedAt == null) {
            clickedAt = Instant.now();
        }
    }
}
