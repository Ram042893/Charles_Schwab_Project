package com.schwab.shortener.orchestration.engine;

import com.schwab.shortener.orchestration.domain.ScenarioType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScenarioCatalog {

    private ScenarioCatalog() {
    }

    public static Map<String, StageNode> graphFor(ScenarioType type) {
        return switch (type) {
            case GREENFIELD -> greenfield();
            case BROWNFIELD -> brownfield();
            case AMBIGUOUS -> ambiguous();
        };
    }

    private static Map<String, StageNode> greenfield() {
        Map<String, StageNode> graph = new LinkedHashMap<>();
        graph.put("REQUIREMENT_ANALYSIS", new StageNode("REQUIREMENT_ANALYSIS", "requirement", List.of()));
        graph.put("TASK_DECOMPOSITION", new StageNode("TASK_DECOMPOSITION", "decomposition", List.of("REQUIREMENT_ANALYSIS")));
        graph.put("ARCHITECTURE_DESIGN", new StageNode("ARCHITECTURE_DESIGN", "architecture", List.of("TASK_DECOMPOSITION")));
        graph.put("IMPLEMENTATION", new StageNode("IMPLEMENTATION", "implementation", List.of("ARCHITECTURE_DESIGN"), false, false, true, Set.of("change-control")));
        graph.put("TEST_PLANNING", new StageNode("TEST_PLANNING", "test-planning", List.of("ARCHITECTURE_DESIGN")));
        graph.put("TEST_EXECUTION", new StageNode("TEST_EXECUTION", "testing", List.of("IMPLEMENTATION", "TEST_PLANNING")));
        graph.put("DOCUMENTATION", new StageNode("DOCUMENTATION", "documentation", List.of("TEST_EXECUTION")));
        graph.put("SECURITY_COMPLIANCE", new StageNode("SECURITY_COMPLIANCE", "security", List.of("TEST_EXECUTION")));
        graph.put("RELEASE_APPROVAL_GATE", new StageNode("RELEASE_APPROVAL_GATE", "gate", List.of("DOCUMENTATION", "SECURITY_COMPLIANCE"), true, true, false, Set.of("human-approval")));
        graph.put("RELEASE_READINESS", new StageNode("RELEASE_READINESS", "release", List.of("RELEASE_APPROVAL_GATE")));
        return graph;
    }

    private static Map<String, StageNode> brownfield() {
        Map<String, StageNode> graph = new LinkedHashMap<>();
        graph.put("REQUIREMENT_ANALYSIS", new StageNode("REQUIREMENT_ANALYSIS", "requirement", List.of()));
        graph.put("IMPACT_ANALYSIS", new StageNode("IMPACT_ANALYSIS", "impact", List.of("REQUIREMENT_ANALYSIS")));
        graph.put("CHANGE_CONTROL_GATE", new StageNode("CHANGE_CONTROL_GATE", "gate", List.of("IMPACT_ANALYSIS"), true, true, false, Set.of("human-approval", "change-control")));
        graph.put("TASK_DECOMPOSITION", new StageNode("TASK_DECOMPOSITION", "decomposition", List.of("CHANGE_CONTROL_GATE")));
        graph.put("ARCHITECTURE_DESIGN", new StageNode("ARCHITECTURE_DESIGN", "architecture", List.of("TASK_DECOMPOSITION")));
        graph.put("IMPLEMENTATION", new StageNode("IMPLEMENTATION", "implementation", List.of("ARCHITECTURE_DESIGN"), false, true, true, Set.of("change-control")));
        graph.put("REGRESSION_PLAN", new StageNode("REGRESSION_PLAN", "test-planning", List.of("ARCHITECTURE_DESIGN")));
        graph.put("TEST_EXECUTION", new StageNode("TEST_EXECUTION", "testing", List.of("IMPLEMENTATION", "REGRESSION_PLAN")));
        graph.put("ROLLBACK_PLAN_VALIDATION", new StageNode("ROLLBACK_PLAN_VALIDATION", "rollback-check", List.of("TEST_EXECUTION")));
        graph.put("DOCUMENTATION", new StageNode("DOCUMENTATION", "documentation", List.of("ROLLBACK_PLAN_VALIDATION")));
        graph.put("SECURITY_COMPLIANCE", new StageNode("SECURITY_COMPLIANCE", "security", List.of("ROLLBACK_PLAN_VALIDATION")));
        graph.put("RELEASE_APPROVAL_GATE", new StageNode("RELEASE_APPROVAL_GATE", "gate", List.of("DOCUMENTATION", "SECURITY_COMPLIANCE"), true, true, false, Set.of("human-approval")));
        graph.put("RELEASE_READINESS", new StageNode("RELEASE_READINESS", "release", List.of("RELEASE_APPROVAL_GATE")));
        return graph;
    }

    private static Map<String, StageNode> ambiguous() {
        Map<String, StageNode> graph = new LinkedHashMap<>();
        graph.put("REQUIREMENT_ANALYSIS", new StageNode("REQUIREMENT_ANALYSIS", "requirement", List.of()));
        graph.put("AMBIGUITY_DETECTION", new StageNode("AMBIGUITY_DETECTION", "ambiguity", List.of("REQUIREMENT_ANALYSIS")));
        graph.put("CLARIFICATION_GATE", new StageNode("CLARIFICATION_GATE", "gate", List.of("AMBIGUITY_DETECTION"), true, true, false, Set.of("human-approval")));
        graph.put("DYNAMIC_REPLAN", new StageNode("DYNAMIC_REPLAN", "replan", List.of("CLARIFICATION_GATE")));
        graph.put("TASK_DECOMPOSITION", new StageNode("TASK_DECOMPOSITION", "decomposition", List.of("DYNAMIC_REPLAN")));
        graph.put("ARCHITECTURE_DESIGN", new StageNode("ARCHITECTURE_DESIGN", "architecture", List.of("TASK_DECOMPOSITION")));
        graph.put("IMPLEMENTATION", new StageNode("IMPLEMENTATION", "implementation", List.of("ARCHITECTURE_DESIGN"), false, false, true, Set.of("change-control")));
        graph.put("TEST_PLANNING", new StageNode("TEST_PLANNING", "test-planning", List.of("ARCHITECTURE_DESIGN")));
        graph.put("TEST_EXECUTION", new StageNode("TEST_EXECUTION", "testing", List.of("IMPLEMENTATION", "TEST_PLANNING")));
        graph.put("DOCUMENTATION", new StageNode("DOCUMENTATION", "documentation", List.of("TEST_EXECUTION")));
        graph.put("SECURITY_COMPLIANCE", new StageNode("SECURITY_COMPLIANCE", "security", List.of("TEST_EXECUTION")));
        graph.put("RELEASE_APPROVAL_GATE", new StageNode("RELEASE_APPROVAL_GATE", "gate", List.of("DOCUMENTATION", "SECURITY_COMPLIANCE"), true, true, false, Set.of("human-approval")));
        graph.put("RELEASE_READINESS", new StageNode("RELEASE_READINESS", "release", List.of("RELEASE_APPROVAL_GATE")));
        return graph;
    }
}
