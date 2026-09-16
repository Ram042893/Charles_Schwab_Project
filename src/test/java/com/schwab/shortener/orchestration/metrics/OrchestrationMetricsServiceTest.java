package com.schwab.shortener.orchestration.metrics;

import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowInstanceRepository;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrationMetricsServiceTest {

    @Mock
    private WorkflowInstanceRepository workflows;

    @Test
    void aggregatesCompletedFailedRetriesAndLatency() {
        WorkflowInstance completed = workflow(WorkflowStatus.COMPLETED, 0, 0,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:10Z"));
        WorkflowInstance failed = workflow(WorkflowStatus.FAILED, 2, 0,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:05Z"));
        WorkflowInstance rolledBack = workflow(WorkflowStatus.ROLLED_BACK, 1, 1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:20Z"));
        when(workflows.findAll()).thenReturn(List.of(completed, failed, rolledBack));

        OrchestrationMetricsService.MetricsSnapshot snapshot = new OrchestrationMetricsService(workflows).snapshot();

        assertThat(snapshot.workflowsStarted()).isEqualTo(3);
        assertThat(snapshot.workflowsCompleted()).isEqualTo(1);
        assertThat(snapshot.workflowsFailed()).isEqualTo(2);
        assertThat(snapshot.retryCount()).isEqualTo(3);
        assertThat(snapshot.rollbackCount()).isEqualTo(1);
        assertThat(snapshot.successRatePercent()).isEqualTo(100.0 / 3);
        assertThat(snapshot.averageEndToEndLatencyMs()).isGreaterThan(0);
        assertThat(snapshot.mttrMs()).isEqualTo(20_000.0);
    }

    @Test
    void emptyRepositoryYieldsZeroSnapshot() {
        when(workflows.findAll()).thenReturn(List.of());
        OrchestrationMetricsService.MetricsSnapshot snapshot = new OrchestrationMetricsService(workflows).snapshot();
        assertThat(snapshot.workflowsStarted()).isZero();
        assertThat(snapshot.successRatePercent()).isZero();
        assertThat(snapshot.averageEndToEndLatencyMs()).isZero();
    }

    private static WorkflowInstance workflow(
            WorkflowStatus status,
            int retries,
            int rollbacks,
            Instant createdAt,
            Instant completedAt
    ) {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(java.util.UUID.randomUUID().toString());
        workflow.setScenarioType(ScenarioType.GREENFIELD);
        workflow.setStatus(status);
        workflow.setRequirementText("req");
        workflow.setStartedBy("engineer");
        workflow.setRetryCount(retries);
        workflow.setRollbackCount(rollbacks);
        workflow.setCreatedAt(createdAt);
        workflow.setCompletedAt(completedAt);
        return workflow;
    }
}
