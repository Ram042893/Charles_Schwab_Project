package com.schwab.shortener.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {
    Optional<ApprovalRequest> findFirstByWorkflowIdAndStatus(String workflowId, String status);
}
