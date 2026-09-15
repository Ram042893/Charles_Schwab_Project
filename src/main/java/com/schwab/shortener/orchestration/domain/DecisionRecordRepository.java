package com.schwab.shortener.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, String> {
    List<DecisionRecord> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
