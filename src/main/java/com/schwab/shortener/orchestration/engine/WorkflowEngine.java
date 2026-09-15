package com.schwab.shortener.orchestration.engine;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.config.AppProperties;
import com.schwab.shortener.orchestration.agent.AgentRegistry;
import com.schwab.shortener.orchestration.agent.AgentResult;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSetRepository;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class WorkflowEngine {

    private final WorkflowInstanceRepository workflows;
    private final StageExecutionRepository stages;
    private final DecisionRecordRepository decisions;
    private final AuditEventRepository audits;
    private final ApprovalRequestRepository approvals;
    private final ProposedChangeSetRepository changeSets;
    private final AgentRegistry agents;
    private final PolicyGuardrail policyGuardrail;
    private final RollbackCoordinator rollbackCoordinator;
    private final AppProperties properties;
    private final TransactionTemplate transactions;
    private final Executor orchestrationExecutor;

    public WorkflowEngine(
            WorkflowInstanceRepository workflows,
            StageExecutionRepository stages,
            DecisionRecordRepository decisions,
            AuditEventRepository audits,
            ApprovalRequestRepository approvals,
            ProposedChangeSetRepository changeSets,
            AgentRegistry agents,
            PolicyGuardrail policyGuardrail,
            RollbackCoordinator rollbackCoordinator,
            AppProperties properties,
            TransactionTemplate transactions,
            @Qualifier("orchestrationExecutor") Executor orchestrationExecutor
    ) {
        this.workflows = workflows;
        this.stages = stages;
        this.decisions = decisions;
        this.audits = audits;
        this.approvals = approvals;
        this.changeSets = changeSets;
        this.agents = agents;
        this.policyGuardrail = policyGuardrail;
        this.rollbackCoordinator = rollbackCoordinator;
        this.properties = properties;
        this.transactions = transactions;
        this.orchestrationExecutor = orchestrationExecutor;
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
            if (workflow.isSafeStopRequested() || workflow.getStatus() == WorkflowStatus.STOPPED) {
                throw new BusinessException(409, "Workflow was safe-stopped and cannot continue");
            }
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

    public WorkflowInstance replan(String workflowId, String actor, String note, String changedStageId) {
        transactions.executeWithoutResult(status -> {
            WorkflowInstance workflow = requireWorkflow(workflowId);
            Map<String, String> context = readContext(workflow);
            context.put("replanNote", note == null ? "upstream changed" : note);
            if (changedStageId != null && !changedStageId.isBlank()) {
                context.put("changedUpstreamStage", changedStageId);
            }
            workflow.setContextJson(writeContext(context));

            Map<String, StageNode> graph = ScenarioCatalog.graphFor(workflow.getScenarioType());
            Set<String> resetIds = selectStagesToReset(graph, stages.findByWorkflowId(workflowId), context, changedStageId);
            for (StageExecution execution : stages.findByWorkflowId(workflowId)) {
                if (resetIds.contains(execution.getStageId())
                        && (execution.getStatus() == StageStatus.COMPLETED
                        || execution.getStatus() == StageStatus.FAILED
                        || execution.getStatus() == StageStatus.ROLLED_BACK
                        || execution.getStatus() == StageStatus.WAITING_APPROVAL)) {
                    execution.setStatus(StageStatus.PENDING);
                    execution.setArtifactJson(null);
                    execution.setErrorMessage(null);
                    execution.setFinishedAt(null);
                    execution.setStartedAt(null);
                }
            }
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflow.setCurrentGate(null);
            recordDecision(workflowId, changedStageId, actor, "REPLAN",
                    "Selective reset of " + resetIds + (note == null ? "" : ": " + note));
            audit(workflowId, "REPLAN", String.join(",", resetIds));
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

    public Map<String, Object> latestChangeSet(String workflowId) {
        requireWorkflow(workflowId);
        ProposedChangeSet changeSet = changeSets.findFirstByWorkflowIdOrderByCreatedAtDesc(workflowId)
                .orElseThrow(() -> new BusinessException(404, "No change set for workflow"));
        return Map.of(
                "id", changeSet.getId(),
                "workflowId", changeSet.getWorkflowId(),
                "mode", changeSet.getMode(),
                "contentHash", changeSet.getContentHash(),
                "files", changeSet.getFilesJson(),
                "unifiedDiff", changeSet.getUnifiedDiff(),
                "createdAt", changeSet.getCreatedAt().toString()
        );
    }

    private Set<String> selectStagesToReset(
            Map<String, StageNode> graph,
            List<StageExecution> executions,
            Map<String, String> context,
            String changedStageId
    ) {
        Set<String> seeds = new LinkedHashSet<>();
        if (changedStageId != null && !changedStageId.isBlank()) {
            seeds.add(changedStageId);
        } else {
            for (StageExecution execution : executions) {
                if (execution.getStatus() != StageStatus.COMPLETED || execution.getArtifactJson() == null) {
                    continue;
                }
                String key = "artifactHash." + execution.getStageId();
                String previous = context.get(key);
                String current = Integer.toHexString(execution.getArtifactJson().hashCode());
                if (previous != null && !previous.equals(current)) {
                    seeds.add(execution.getStageId());
                }
            }
            if (seeds.isEmpty()) {
                seeds.add("TASK_DECOMPOSITION");
            }
        }
        Set<String> reset = new LinkedHashSet<>(seeds);
        ArrayDeque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (StageNode node : graph.values()) {
                if (node.dependsOn().contains(current) && reset.add(node.id())) {
                    queue.add(node.id());
                }
            }
        }
        return reset;
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
        if (stageIds.size() <= 1) {
            for (String stageId : stageIds) {
                transactions.executeWithoutResult(status -> executeStage(workflowId, stageId));
            }
            return;
        }
        List<CompletableFuture<Void>> futures = stageIds.stream()
                .map(stageId -> CompletableFuture.runAsync(
                        () -> transactions.executeWithoutResult(status -> executeStage(workflowId, stageId)),
                        orchestrationExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void executeStage(String workflowId, String stageId) {
        WorkflowInstance workflow = requireWorkflow(workflowId);
        if (workflow.isSafeStopRequested()) {
            StageExecution execution = stages.findByWorkflowIdAndStageId(workflowId, stageId).orElseThrow();
            execution.setStatus(StageStatus.SKIPPED);
            execution.setErrorMessage("Skipped due to safe-stop");
            execution.setFinishedAt(Instant.now());
            return;
        }
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
                sleepBackoff();
            }
            try {
                AgentResult result = runAgent(workflow, node, context);
                persistAgentSuccess(workflow, execution, node, context, result, workflowId, stageId);
                return;
            } catch (Exception ex) {
                lastError = ex;
                execution.setErrorMessage(ex.getMessage());
            }
        }

        if ("testing".equals(node.agent()) && !"true".equals(context.get("repairAttempted"))) {
            try {
                context.put("lastTestError", lastError == null ? "validation failed" : lastError.getMessage());
                context.put("repairAttempted", "true");
                AgentResult repair = agents.require("repair").execute(workflow, node, context);
                recordDecision(workflowId, stageId, "repair", repair.success() ? "REPAIR_OK" : "REPAIR_FAIL", repair.rationale());
                audit(workflowId, "VALIDATION_REPAIR", stageId);
                agents.require("implementation").execute(workflow, node, context);
                AgentResult retest = runAgent(workflow, node, context);
                persistAgentSuccess(workflow, execution, node, context, retest, workflowId, stageId);
                return;
            } catch (Exception repairEx) {
                lastError = repairEx;
                execution.setErrorMessage(repairEx.getMessage());
            }
        }

        if (("implementation".equals(node.agent()) || "testing".equals(node.agent()))
                && !"true".equals(context.get("fallbackUsed"))) {
            try {
                audit(workflowId, "FALLBACK_STARTED", stageId);
                AgentResult fallback = agents.require("fallback").execute(workflow, node, context);
                recordDecision(workflowId, stageId, "fallback", fallback.success() ? "FALLBACK_OK" : "FALLBACK_FAIL", fallback.rationale());
                workflow.setRetryCount(workflow.getRetryCount() + 1);
                if ("testing".equals(node.agent())) {
                    agents.require("implementation").execute(workflow, node, context);
                }
                AgentResult recovered = runAgent(workflow, node, context);
                persistAgentSuccess(workflow, execution, node, context, recovered, workflowId, stageId);
                audit(workflowId, "FALLBACK_SUCCEEDED", stageId);
                return;
            } catch (Exception fallbackEx) {
                lastError = fallbackEx;
                execution.setErrorMessage(fallbackEx.getMessage());
                audit(workflowId, "FALLBACK_FAILED", stageId + ":" + fallbackEx.getMessage());
            }
        }

        execution.setStatus(StageStatus.FAILED);
        execution.setFinishedAt(Instant.now());
        workflow.setContextJson(writeContext(context));
        audit(workflowId, "STAGE_FAILED", stageId + ":" + (lastError == null ? "unknown" : lastError.getMessage()));
    }

    private AgentResult runAgent(WorkflowInstance workflow, StageNode node, Map<String, String> context) {
        policyGuardrail.assertNoSecrets(workflow.getRequirementText());
        AgentResult result = agents.require(node.agent()).execute(workflow, node, context);
        if (!result.success()) {
            throw new IllegalStateException(result.rationale());
        }
        return result;
    }

    private void persistAgentSuccess(
            WorkflowInstance workflow,
            StageExecution execution,
            StageNode node,
            Map<String, String> context,
            AgentResult result,
            String workflowId,
            String stageId
    ) {
        if (result.artifactJson() != null) {
            context.put("artifactHash." + stageId, Integer.toHexString(result.artifactJson().hashCode()));
        }
        workflow.setContextJson(writeContext(context));
        execution.setArtifactJson(result.artifactJson());
        recordDecision(workflowId, stageId, node.agent(), "AGENT_OK", result.rationale());
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
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(properties.orchestration().retryBackoff().toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
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
        boolean allComplete = executions.stream().allMatch(item ->
                item.getStatus() == StageStatus.COMPLETED || item.getStatus() == StageStatus.SKIPPED);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow", workflowView(workflow));
        body.put("stages", stages.findByWorkflowId(workflowId));
        body.put("decisions", decisions.findByWorkflowIdOrderByCreatedAtAsc(workflowId));
        body.put("audit", audits.findByWorkflowIdOrderByCreatedAtAsc(workflowId));
        changeSets.findFirstByWorkflowIdOrderByCreatedAtDesc(workflowId).ifPresent(changeSet ->
                body.put("changeSet", Map.of(
                        "id", changeSet.getId(),
                        "mode", changeSet.getMode(),
                        "contentHash", changeSet.getContentHash(),
                        "files", changeSet.getFilesJson(),
                        "unifiedDiff", changeSet.getUnifiedDiff()
                )));
        return body;
    }

    public Map<String, Object> workflowView(WorkflowInstance workflow) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", workflow.getId());
        view.put("scenarioType", workflow.getScenarioType().name());
        view.put("status", workflow.getStatus().name());
        view.put("currentGate", workflow.getCurrentGate() == null ? "" : workflow.getCurrentGate());
        view.put("startedBy", workflow.getStartedBy());
        view.put("retryCount", workflow.getRetryCount());
        view.put("rollbackCount", workflow.getRollbackCount());
        view.put("createdAt", workflow.getCreatedAt().toString());
        view.put("graph", SimpleJson.literal(ScenarioCatalog.graphFor(workflow.getScenarioType()).keySet()));
        return view;
    }
}
