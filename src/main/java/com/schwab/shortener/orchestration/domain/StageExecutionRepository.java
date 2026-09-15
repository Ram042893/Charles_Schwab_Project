package com.schwab.shortener.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageExecutionRepository extends JpaRepository<StageExecution, String> {
    List<StageExecution> findByWorkflowId(String workflowId);
    Optional<StageExecution> findByWorkflowIdAndStageId(String workflowId, String stageId);
}
