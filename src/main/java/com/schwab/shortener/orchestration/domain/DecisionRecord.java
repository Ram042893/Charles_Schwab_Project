package com.schwab.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "decision_records")
public class DecisionRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String workflowId;

    @Column(length = 40)
    private String stageId;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(nullable = false, length = 40)
    private String decisionType;

    @Lob
    private String rationale;

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
