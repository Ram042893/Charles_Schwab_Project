package com.schwab.shortener.orchestration.engine;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.config.AppProperties;
import com.schwab.shortener.orchestration.agent.AgentRegistry;
import com.schwab.shortener.orchestration.agent.AgentResult;
import com.schwab.shortener.orchestration.domain.ApprovalRequest;
import com.schwab.shortener.orchestration.domain.ApprovalRequestRepository;
import com.schwab.shortener.orchestration.domain.AuditEvent;
import com.schwab.shortener.orchestration.domain.AuditEventRepository;
import com.schwab.shortener.orchestration.domain.DecisionRecord;
import com.schwab.shortener.orchestration.domain.DecisionRecordRepository;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.StageExecution;
import com.schwab.shortener.orchestration.domain.StageExecutionRepository;
import com.schwab.shortener.orchestration.domain.StageStatus;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowInstanceRepository;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import com.schwab.shortener.orchestration.governance.PolicyGuardrail;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowEngine {

    private final WorkflowInstanceRepository workflows;
    private final StageExecutionRepository stages;
    private final DecisionRecordRepository decisions;
    private final AuditEventRepository audits;
    private final ApprovalRequestRepository approvals;
    private final AgentRegistry agents;
    private final PolicyGuardrail policyGuardrail;
    private final RollbackCoordinator rollbackCoordinator;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    public WorkflowEngine(
            WorkflowInstanceRepository workflows,
            StageExecutionRepository stages,
            DecisionRecordRepository decisions,
            AuditEventRepository audits,
            ApprovalRequestRepository approvals,
            AgentRegistry agents,
            PolicyGuardrail policyGuardrail,
            RollbackCoordinator rollbackCoordinator,
            AppProperties properties,
            TransactionTemplate transactions
    ) {
        this.workflows = workflows;
        this.stages = stages;
        this.decisions = decisions;
        this.audits = audits;
        this.approvals = approvals;
        this.agents = agents;
        this.policyGuardrail = policyGuardrail;
        this.rollbackCoordinator = rollbackCoordinator;
        this.properties = properties;
        this.transactions = transactions;
    }

    public WorkflowInstance start(ScenarioType type, String requirement, String actor) {
        policyGuardrail.assertNoSecrets(requirement);
        String workflowId = transactions.execute(status -> {
            WorkflowInstance workflow = new WorkflowInstance();
            workflow.setScenarioType(type);
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflow.setRequirementText(requirement);
            workflow.setStartedBy(actor);
            workflow.setContextJson("");
            workflow.setRetryCount(0);
            workflow.setRollbackCount(0);
            workflows.save(workflow);
            ScenarioCatalog.graphFor(type).values().forEach(node -> {
                StageExecution execution = new StageExecution();
                execution.setWorkflowId(workflow.getId());
                execution.setStageId(node.id());
                execution.setStatus(StageStatus.PENDING);
                execution.setAttempt(1);
                stages.save(execution);
            });
            recordDecision(workflow.getId(), null, actor, "START", "Started " + type + " workflow");
            audit(workflow.getId(), "WORKFLOW_STARTED", type.name());
            return workflow.getId();
        });
        return advance(workflowId);
    }

    public WorkflowInstance advance(String workflowId) {
        MDC.put("workflowId", workflowId);
        try {
            while (true) {
                AdvanceDecision decision = transactions.execute(status -> prepareWave(workflowId));
                if (decision == null) {
                    throw new BusinessException(404, "Workflow not found");
                }
                if (decision.done()) {
                    return requireWorkflow(workflowId);
                }
                runWave(workflowId, decision.readyStageIds());
                WorkflowStatus after = transactions.execute(status -> settleWave(workflowId));
                if (after != WorkflowStatus.RUNNING) {
                    return requireWorkflow(workflowId);
                }
            }
        } finally {
            MDC.remove("workflowId");
        }
    }

    public WorkflowInstance approve(String workflowId, String actor, String comment) {
        transactions.executeWithoutResult(status -> {
            WorkflowInstance workflow = requireWorkflow(workflowId);
            ApprovalRequest request = approvals.findFirstByWorkflowIdAndStatus(workflowId, "PENDING")
                    .orElseThrow(() -> new BusinessException(409, "No pending approval"));
            request.setStatus("APPROVED");
            request.setDecidedBy(actor);
            request.setComment(comment);
            request.setDecidedAt(Instant.now());
            StageExecution execution = stages.findByWorkflowIdAndStageId(workflowId, request.getStageId()).orElseThrow();
            execution.setStatus(StageStatus.COMPLETED);
            execution.setFinishedAt(Instant.now());
            if ("CLARIFICATION_GATE".equals(request.getStageId())) {
                Map<String, String> context = readContext(workflow);
                context.put("approvedAssumptions", "custom-alias,expiration,analytics-export");
                workflow.setContextJson(writeContext(context));
            }
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflow.setCurrentGate(null);
            workflow.setUpdatedAt(Instant.now());
            recordDecision(workflowId, request.getStageId(), actor, "APPROVE", comment == null ? "Approved" : comment);
            audit(workflowId, "APPROVED", request.getStageId());
        });
        return advance(workflowId);
    }

    public WorkflowInstance reject(String workflowId, String actor, String comment) {
        return transactions.execute(status -> {
            WorkflowInstance workflow = requireWorkflow(workflowId);
            ApprovalRequest request = approvals.findFirstByWorkflowIdAndStatus(workflowId, "PENDING")
                    .orElseThrow(() -> new BusinessException(409, "No pending approval"));
            request.setStatus("REJECTED");
            request.setDecidedBy(actor);
            request.setComment(comment);
            request.setDecidedAt(Instant.now());
            StageExecution execution = stages.findByWorkflowIdAndStageId(workflowId, request.getStageId()).orElseThrow();
            execution.setStatus(StageStatus.FAILED);
            execution.setErrorMessage(comment);
            execution.setFinishedAt(Instant.now());
            workflow.setStatus(WorkflowStatus.STOPPED);
            workflow.setStopReason("Rejected at " + request.getStageId());
            workflow.setCompletedAt(Instant.now());
            recordDecision(workflowId, request.getStageId(), actor, "REJECT", comment);
            audit(workflowId, "REJECTED", request.getStageId());
            return workflow;
        });
    }

    public WorkflowInstance replan(String workflowId, String actor, String note) {
        transactions.executeWithoutResult(status -> {
            WorkflowInstance workflow = requireWorkflow(workflowId);
            Map<String, String> context = readContext(workflow);
            context.put("replanNote", note == null ? "upstream changed" : note);
            workflow.setContextJson(writeContext(context));
            workflow.setStatus(WorkflowStatus.REPLANNED);
            List<StageExecution> executions = stages.findByWorkflowId(workflowId);
            boolean reset = false;
            for (StageExecution execution : executions) {
                if ("TASK_DECOMPOSITION".equals(execution.getStageId()) || reset) {
                    reset = true;
                    if (execution.getStatus() == StageStatus.COMPLETED || execution.getStatus() == StageStatus.FAILED) {
                        execution.setStatus(StageStatus.PENDING);
                        execution.setArtifactJson(null);
                        execution.setErrorMessage(null);
                        execution.setFinishedAt(null);
                    }
                }
            }
            workflow.setStatus(WorkflowStatus.RUNNING);
            recordDecision(workflowId, null, actor, "REPLAN", note);
            audit(workflowId, "REPLAN", note);
        });
        return advance(workflowId);
    }

    public WorkflowInstance safeStop(String workflowId, String actor, String reason) {
        return transactions.execute(status -> {
            WorkflowInstance workflow = requireWorkflow(workflowId);
            workflow.setSafeStopRequested(true);
            workflow.setStopReason(reason);
            workflow.setStatus(WorkflowStatus.STOPPED);
            workflow.setCompletedAt(Instant.now());
            recordDecision(workflowId, null, actor, "SAFE_STOP", reason);
            audit(workflowId, "SAFE_STOP", reason);
            return workflow;
        });
    }

    public WorkflowInstance get(String workflowId) {
        return requireWorkflow(workflowId);
    }

    private AdvanceDecision prepareWave(String workflowId) {
        WorkflowInstance workflow = requireWorkflow(workflowId);
        if (workflow.isSafeStopRequested()) {
            workflow.setStatus(WorkflowStatus.STOPPED);
            workflow.setCompletedAt(Instant.now());
            return AdvanceDecision.stop();
        }
        if (workflow.getStatus() == WorkflowStatus.WAITING_APPROVAL
                || workflow.getStatus() == WorkflowStatus.COMPLETED
                || workflow.getStatus() == WorkflowStatus.FAILED
                || workflow.getStatus() == WorkflowStatus.STOPPED
                || workflow.getStatus() == WorkflowStatus.ROLLED_BACK) {
            return AdvanceDecision.stop();
        }
        Map<String, StageNode> graph = ScenarioCatalog.graphFor(workflow.getScenarioType());
        List<StageExecution> executions = stages.findByWorkflowId(workflowId);
        List<String> ready = new ArrayList<>();
        for (StageNode node : graph.values()) {
            StageExecution execution = executions.stream().filter(item -> item.getStageId().equals(node.id())).findFirst().orElseThrow();
            if (execution.getStatus() != StageStatus.PENDING) {
                continue;
            }
            boolean depsDone = node.dependsOn().stream().allMatch(dep ->
                    executions.stream().anyMatch(item -> item.getStageId().equals(dep) && item.getStatus() == StageStatus.COMPLETED));
            if (depsDone) {
                execution.setStatus(StageStatus.READY);
                ready.add(node.id());
            }
        }
        if (!ready.isEmpty()) {
            if (ready.size() > 1) {
                audit(workflowId, "PARALLEL_WAVE", String.join(",", ready));
            }
            return new AdvanceDecision(false, ready);
        }
        boolean anyFailed = executions.stream().anyMatch(item -> item.getStatus() == StageStatus.FAILED);
        boolean allComplete = executions.stream().allMatch(item -> item.getStatus() == StageStatus.COMPLETED);
        if (allComplete) {
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflow.setCompletedAt(Instant.now());
            audit(workflowId, "WORKFLOW_COMPLETED", "ok");
            return AdvanceDecision.stop();
        }
        if (anyFailed) {
            rollbackCoordinator.rollback(workflow, executions);
            workflow.setStatus(WorkflowStatus.ROLLED_BACK);
            workflow.setCompletedAt(Instant.now());
            audit(workflowId, "WORKFLOW_ROLLED_BACK", "stage failure");
            return AdvanceDecision.stop();
        }
        return AdvanceDecision.stop();
    }

    private void runWave(String workflowId, List<String> stageIds) {
        for (String stageId : stageIds) {
            transactions.executeWithoutResult(status -> executeStage(workflowId, stageId));
        }
    }

    private void executeStage(String workflowId, String stageId) {
        WorkflowInstance workflow = requireWorkflow(workflowId);
        StageExecution execution = stages.findByWorkflowIdAndStageId(workflowId, stageId).orElseThrow();
        StageNode node = ScenarioCatalog.graphFor(workflow.getScenarioType()).get(stageId);
        execution.setStatus(StageStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        Map<String, String> context = readContext(workflow);
        int maxAttempts = properties.orchestration().maxRetries();
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            execution.setAttempt(attempt);
            if (attempt > 1) {
                execution.setStatus(StageStatus.RETRYING);
                workflow.setRetryCount(workflow.getRetryCount() + 1);
                try {
                    Thread.sleep(properties.orchestration().retryBackoff().toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                policyGuardrail.assertNoSecrets(workflow.getRequirementText());
                AgentResult result = agents.require(node.agent()).execute(workflow, node, context);
                workflow.setContextJson(writeContext(context));
                execution.setArtifactJson(result.artifactJson());
                recordDecision(workflowId, stageId, node.agent(), result.success() ? "AGENT_OK" : "AGENT_FAIL", result.rationale());
                if (!result.success()) {
                    throw new IllegalStateException(result.rationale());
                }
                if (result.requiresApproval() && node.approvalGate()) {
                    ApprovalRequest request = new ApprovalRequest();
                    request.setWorkflowId(workflowId);
                    request.setStageId(stageId);
                    request.setPrompt(result.approvalPrompt());
                    request.setStatus("PENDING");
                    approvals.save(request);
                    execution.setStatus(StageStatus.WAITING_APPROVAL);
                    workflow.setStatus(WorkflowStatus.WAITING_APPROVAL);
                    workflow.setCurrentGate(stageId);
                    audit(workflowId, "WAITING_APPROVAL", stageId);
                    return;
                }
                execution.setStatus(StageStatus.COMPLETED);
                execution.setFinishedAt(Instant.now());
                return;
            } catch (Exception ex) {
                lastError = ex;
                execution.setErrorMessage(ex.getMessage());
            }
        }
        execution.setStatus(StageStatus.FAILED);
        execution.setFinishedAt(Instant.now());
        audit(workflowId, "STAGE_FAILED", stageId + ":" + (lastError == null ? "unknown" : lastError.getMessage()));
    }

    private WorkflowStatus settleWave(String workflowId) {
        WorkflowInstance workflow = requireWorkflow(workflowId);
        List<StageExecution> executions = stages.findByWorkflowId(workflowId);
        if (executions.stream().anyMatch(item -> item.getStatus() == StageStatus.WAITING_APPROVAL)) {
            workflow.setStatus(WorkflowStatus.WAITING_APPROVAL);
            return WorkflowStatus.WAITING_APPROVAL;
        }
        if (executions.stream().anyMatch(item -> item.getStatus() == StageStatus.FAILED)) {
            rollbackCoordinator.rollback(workflow, executions);
            workflow.setStatus(WorkflowStatus.ROLLED_BACK);
            workflow.setCompletedAt(Instant.now());
            return WorkflowStatus.ROLLED_BACK;
        }
        boolean allComplete = executions.stream().allMatch(item -> item.getStatus() == StageStatus.COMPLETED);
        if (allComplete) {
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflow.setCompletedAt(Instant.now());
            return WorkflowStatus.COMPLETED;
        }
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflow.setUpdatedAt(Instant.now());
        return WorkflowStatus.RUNNING;
    }

    private WorkflowInstance requireWorkflow(String workflowId) {
        return workflows.findById(workflowId).orElseThrow(() -> new BusinessException(404, "Workflow not found"));
    }

    private void recordDecision(String workflowId, String stageId, String actor, String type, String rationale) {
        DecisionRecord record = new DecisionRecord();
        record.setWorkflowId(workflowId);
        record.setStageId(stageId);
        record.setActor(actor);
        record.setDecisionType(type);
        record.setRationale(rationale);
        decisions.save(record);
    }

    private void audit(String workflowId, String type, String details) {
        AuditEvent event = new AuditEvent();
        event.setWorkflowId(workflowId);
        event.setEventType(type);
        event.setDetails(details);
        audits.save(event);
    }

    private Map<String, String> readContext(WorkflowInstance workflow) {
        Map<String, String> context = new LinkedHashMap<>();
        if (workflow.getContextJson() == null || workflow.getContextJson().isBlank()) {
            return context;
        }
        for (String line : workflow.getContextJson().split("\n")) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                context.put(line.substring(0, idx), URLDecoder.decode(line.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return context;
    }

    private String writeContext(Map<String, String> context) {
        StringBuilder builder = new StringBuilder();
        context.forEach((key, value) -> builder.append(key).append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)).append('\n'));
        return builder.toString();
    }

    private record AdvanceDecision(boolean done, List<String> readyStageIds) {
        static AdvanceDecision stop() {
            return new AdvanceDecision(true, List.of());
        }
    }

    public Map<String, Object> details(String workflowId) {
        WorkflowInstance workflow = requireWorkflow(workflowId);
        return Map.of(
                "workflow", workflowView(workflow),
                "stages", stages.findByWorkflowId(workflowId),
                "decisions", decisions.findByWorkflowIdOrderByCreatedAtAsc(workflowId),
                "audit", audits.findByWorkflowIdOrderByCreatedAtAsc(workflowId)
        );
    }

    public Map<String, Object> workflowView(WorkflowInstance workflow) {
        return Map.of(
                "id", workflow.getId(),
                "scenarioType", workflow.getScenarioType().name(),
                "status", workflow.getStatus().name(),
                "currentGate", workflow.getCurrentGate() == null ? "" : workflow.getCurrentGate(),
                "startedBy", workflow.getStartedBy(),
                "retryCount", workflow.getRetryCount(),
                "rollbackCount", workflow.getRollbackCount(),
                "createdAt", workflow.getCreatedAt().toString(),
                "graph", SimpleJson.literal(ScenarioCatalog.graphFor(workflow.getScenarioType()).keySet())
        );
    }
}
