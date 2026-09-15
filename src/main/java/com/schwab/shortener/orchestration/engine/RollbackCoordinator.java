package com.schwab.shortener.orchestration.engine;

import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.domain.StageExecution;
import com.schwab.shortener.orchestration.domain.StageStatus;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class RollbackCoordinator {

    private final FeatureFlagService featureFlags;
    private final ChangeSetService changeSets;

    public RollbackCoordinator(FeatureFlagService featureFlags, ChangeSetService changeSets) {
        this.featureFlags = featureFlags;
        this.changeSets = changeSets;
    }

    @Transactional
    public void rollback(WorkflowInstance workflow, List<StageExecution> executions) {
        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
        featureFlags.setEnabled(FeatureFlagService.EXPIRATION, false);
        featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, false);
        changeSets.discard(workflow.getId());
        workflow.setRollbackCount(workflow.getRollbackCount() + 1);
        for (StageExecution execution : executions) {
            if (execution.getStatus() == StageStatus.COMPLETED && "IMPLEMENTATION".equals(execution.getStageId())) {
                execution.setStatus(StageStatus.ROLLED_BACK);
                execution.setFinishedAt(Instant.now());
            }
        }
    }
}
