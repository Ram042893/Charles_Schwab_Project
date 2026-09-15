package com.schwab.shortener.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    List<WorkflowInstance> findAllByOrderByCreatedAtDesc();
}
