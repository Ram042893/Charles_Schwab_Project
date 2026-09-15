package com.schwab.shortener.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "stage_executions")
public class StageExecution {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String workflowId;

    @Column(nullable = false, length = 40)
    private String stageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StageStatus status;

    private int attempt;

    @Lob
    private String artifactJson;

    @Lob
    private String errorMessage;

    private Instant startedAt;
    private Instant finishedAt;

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
        if (attempt == 0) {
            attempt = 1;
        }
    }
}
