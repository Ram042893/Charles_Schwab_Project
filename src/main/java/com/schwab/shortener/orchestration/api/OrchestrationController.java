package com.schwab.shortener.orchestration.api;

import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowInstanceRepository;
import com.schwab.shortener.orchestration.engine.WorkflowEngine;
import com.schwab.shortener.orchestration.metrics.OrchestrationMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orchestration")
@Tag(name = "Agentic Orchestration")
public class OrchestrationController {

    private final WorkflowEngine engine;
    private final WorkflowInstanceRepository workflows;
    private final OrchestrationMetricsService metrics;

    public OrchestrationController(WorkflowEngine engine, WorkflowInstanceRepository workflows, OrchestrationMetricsService metrics) {
        this.engine = engine;
        this.workflows = workflows;
        this.metrics = metrics;
    }

    @PostMapping("/scenarios/greenfield")
    @Operation(summary = "Start the greenfield URL shortener SDLC workflow")
    public Map<String, Object> greenfield(Authentication authentication, @Valid @RequestBody RequirementPayload payload) {
        return started(engine.start(ScenarioType.GREENFIELD, payload.requirement(), authentication.getName()));
    }

    @PostMapping("/scenarios/brownfield")
    @Operation(summary = "Start the brownfield enhancement workflow")
    public Map<String, Object> brownfield(Authentication authentication, @Valid @RequestBody RequirementPayload payload) {
        return started(engine.start(ScenarioType.BROWNFIELD, payload.requirement(), authentication.getName()));
    }

    @PostMapping("/scenarios/ambiguous")
    @Operation(summary = "Start the ambiguous-requirement workflow")
    public Map<String, Object> ambiguous(Authentication authentication, @Valid @RequestBody RequirementPayload payload) {
        return started(engine.start(ScenarioType.AMBIGUOUS, payload.requirement(), authentication.getName()));
    }

    @PostMapping("/workflows/{id}/approve")
    public Map<String, Object> approve(Authentication authentication, @PathVariable String id, @RequestBody(required = false) DecisionPayload payload) {
        return started(engine.approve(id, authentication.getName(), payload == null ? "approved" : payload.comment()));
    }

    @PostMapping("/workflows/{id}/reject")
    public Map<String, Object> reject(Authentication authentication, @PathVariable String id, @RequestBody(required = false) DecisionPayload payload) {
        return started(engine.reject(id, authentication.getName(), payload == null ? "rejected" : payload.comment()));
    }

    @PostMapping("/workflows/{id}/replan")
    public Map<String, Object> replan(Authentication authentication, @PathVariable String id, @RequestBody(required = false) DecisionPayload payload) {
        return started(engine.replan(id, authentication.getName(), payload == null ? "upstream changed" : payload.comment()));
    }

    @PostMapping("/workflows/{id}/safe-stop")
    public Map<String, Object> safeStop(Authentication authentication, @PathVariable String id, @RequestBody(required = false) DecisionPayload payload) {
        return started(engine.safeStop(id, authentication.getName(), payload == null ? "operator stop" : payload.comment()));
    }

    @GetMapping("/workflows/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return engine.details(id);
    }

    @GetMapping("/workflows")
    public List<Map<String, Object>> list() {
        return workflows.findAllByOrderByCreatedAtDesc().stream().map(engine::workflowView).toList();
    }

    @GetMapping("/metrics")
    public OrchestrationMetricsService.MetricsSnapshot metrics() {
        return metrics.snapshot();
    }

    private Map<String, Object> started(WorkflowInstance workflow) {
        return engine.details(workflow.getId());
    }

    public record RequirementPayload(@NotBlank String requirement) {}

    public record DecisionPayload(String comment) {}
}
