package com.schwab.shortener.orchestration.changeset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProposedChangeSetRepository extends JpaRepository<ProposedChangeSet, String> {
    List<ProposedChangeSet> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);

    Optional<ProposedChangeSet> findFirstByWorkflowIdOrderByCreatedAtDesc(String workflowId);
}
