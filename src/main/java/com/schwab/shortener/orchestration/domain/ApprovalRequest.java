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
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String workflowId;

    @Column(nullable = false, length = 40)
    private String stageId;

    @Column(nullable = false, length = 20)
    private String status;

    @Lob
    private String prompt;

    private String decidedBy;
    private String comment;
    private Instant decidedAt;

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
        if (status == null) {
            status = "PENDING";
        }
    }
}
