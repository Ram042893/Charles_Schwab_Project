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
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScenarioType scenarioType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkflowStatus status;

    @Lob
    @Column(nullable = false)
    private String requirementText;

    @Lob
    private String contextJson;

    @Column(nullable = false, length = 80)
    private String startedBy;

    private String currentGate;

    private boolean safeStopRequested;

    private String stopReason;

    private int retryCount;

    private int rollbackCount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
}
