package com.schwab.shortener.orchestration.metrics;

import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowInstanceRepository;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OrchestrationMetricsService {

    private final WorkflowInstanceRepository workflows;

    public OrchestrationMetricsService(WorkflowInstanceRepository workflows) {
        this.workflows = workflows;
    }

    public MetricsSnapshot snapshot() {
        List<WorkflowInstance> all = workflows.findAll();
        long total = all.size();
        long completed = all.stream().filter(item -> item.getStatus() == WorkflowStatus.COMPLETED).count();
        long failed = all.stream().filter(item -> item.getStatus() == WorkflowStatus.FAILED || item.getStatus() == WorkflowStatus.ROLLED_BACK).count();
        long retries = all.stream().mapToLong(WorkflowInstance::getRetryCount).sum();
        long rollbacks = all.stream().mapToLong(WorkflowInstance::getRollbackCount).sum();
        double successRate = total == 0 ? 0 : (completed * 100.0) / total;
        double avgLatencyMs = all.stream()
                .filter(item -> item.getCompletedAt() != null)
                .mapToLong(item -> Duration.between(item.getCreatedAt(), item.getCompletedAt()).toMillis())
                .average()
                .orElse(0);
        double mttrMs = all.stream()
                .filter(item -> item.getRollbackCount() > 0 && item.getCompletedAt() != null)
                .mapToLong(item -> Duration.between(item.getCreatedAt(), item.getCompletedAt()).toMillis())
                .average()
                .orElse(0);
        return new MetricsSnapshot(total, completed, failed, successRate, retries, rollbacks, avgLatencyMs, mttrMs);
    }

    public record MetricsSnapshot(
            long workflowsStarted,
            long workflowsCompleted,
            long workflowsFailed,
            double successRatePercent,
            long retryCount,
            long rollbackCount,
            double averageEndToEndLatencyMs,
            double mttrMs
    ) {}
}
