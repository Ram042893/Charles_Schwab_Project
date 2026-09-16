package com.schwab.shortener.orchestration.engine;

import com.schwab.shortener.orchestration.domain.ScenarioType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCatalogTest {

    @Test
    void greenfieldHasParallelImplAndTestPlanningJoiningAtTestExecution() {
        Map<String, StageNode> graph = ScenarioCatalog.graphFor(ScenarioType.GREENFIELD);
        assertThat(graph).containsKeys("IMPLEMENTATION", "TEST_PLANNING", "TEST_EXECUTION", "RELEASE_APPROVAL_GATE");
        assertThat(graph.get("IMPLEMENTATION").dependsOn()).containsExactly("ARCHITECTURE_DESIGN");
        assertThat(graph.get("TEST_PLANNING").dependsOn()).containsExactly("ARCHITECTURE_DESIGN");
        assertThat(graph.get("TEST_EXECUTION").dependsOn()).containsExactlyInAnyOrder("IMPLEMENTATION", "TEST_PLANNING");
        assertThat(graph.get("RELEASE_APPROVAL_GATE").approvalGate()).isTrue();
        assertThat(graph.get("RELEASE_APPROVAL_GATE").highImpact()).isTrue();
    }

    @Test
    void brownfieldRequiresChangeControlBeforeDecomposition() {
        Map<String, StageNode> graph = ScenarioCatalog.graphFor(ScenarioType.BROWNFIELD);
        assertThat(graph).containsKeys("IMPACT_ANALYSIS", "CHANGE_CONTROL_GATE", "ROLLBACK_PLAN_VALIDATION");
        assertThat(graph.get("CHANGE_CONTROL_GATE").approvalGate()).isTrue();
        assertThat(graph.get("TASK_DECOMPOSITION").dependsOn()).containsExactly("CHANGE_CONTROL_GATE");
        assertThat(graph.get("IMPLEMENTATION").rollbackable()).isTrue();
    }

    @Test
    void ambiguousRoutesThroughClarificationAndReplan() {
        Map<String, StageNode> graph = ScenarioCatalog.graphFor(ScenarioType.AMBIGUOUS);
        assertThat(graph).containsKeys("AMBIGUITY_DETECTION", "CLARIFICATION_GATE", "DYNAMIC_REPLAN");
        assertThat(graph.get("DYNAMIC_REPLAN").dependsOn()).containsExactly("CLARIFICATION_GATE");
        assertThat(graph.get("TASK_DECOMPOSITION").dependsOn()).containsExactly("DYNAMIC_REPLAN");
    }
}
