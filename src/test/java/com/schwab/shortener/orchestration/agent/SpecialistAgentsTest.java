package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialistAgentsTest {

    @Test
    void requirementAgentAddsBrownfieldEnhancements() {
        RequirementAgent agent = new RequirementAgent();
        WorkflowInstance workflow = workflow(ScenarioType.BROWNFIELD, "Add aliases");
        Map<String, String> context = new HashMap<>();
        AgentResult result = agent.execute(workflow, node("REQUIREMENT_ANALYSIS", "requirement"), context);
        assertThat(result.success()).isTrue();
        assertThat(result.artifactJson()).contains("CUSTOM_ALIAS");
        assertThat(context).containsKey("normalizedSpec");
    }

    @Test
    void decompositionAndAmbiguityAndImpactProduceArtifacts() {
        WorkflowInstance workflow = workflow(ScenarioType.AMBIGUOUS, "Make it better");
        Map<String, String> context = new HashMap<>();

        AgentResult decomposed = new DecompositionAgent().execute(workflow, node("TASK_DECOMPOSITION", "decomposition"), context);
        assertThat(decomposed.artifactJson()).contains("Auth and identity");
        assertThat(context).containsKey("taskGraph");

        AgentResult ambiguity = new AmbiguityAgent().execute(workflow, node("AMBIGUITY_DETECTION", "ambiguity"), context);
        assertThat(ambiguity.artifactJson()).contains("openQuestions");
        assertThat(context).containsKey("ambiguity");

        AgentResult impact = new ImpactAnalysisAgent().execute(workflow, node("IMPACT_ANALYSIS", "impact"), context);
        assertThat(impact.artifactJson()).contains("requiresChangeControl");
        assertThat(context).containsKey("impact");
    }

    @Test
    void gateAgentRequiresApprovalWithScenarioSpecificPrompt() {
        GateAgent agent = new GateAgent();
        WorkflowInstance workflow = workflow(ScenarioType.BROWNFIELD, "Enhance shortener");

        AgentResult changeControl = agent.execute(workflow,
                new StageNode("CHANGE_CONTROL_GATE", "gate", List.of(), true, true, false, java.util.Set.of("human-approval")),
                new HashMap<>());
        assertThat(changeControl.requiresApproval()).isTrue();
        assertThat(changeControl.approvalPrompt()).contains("feature-flag");

        AgentResult clarification = agent.execute(workflow,
                new StageNode("CLARIFICATION_GATE", "gate", List.of(), true, true, false, java.util.Set.of("human-approval")),
                new HashMap<>());
        assertThat(clarification.approvalPrompt()).contains("custom aliases");
    }

    @Test
    void replanAgentWritesPlanFromAssumptions() {
        ReplanAgent agent = new ReplanAgent();
        Map<String, String> context = new HashMap<>();
        context.put("approvedAssumptions", "custom-alias");
        AgentResult result = agent.execute(workflow(ScenarioType.AMBIGUOUS, "clarify"), node("DYNAMIC_REPLAN", "replan"), context);
        assertThat(result.success()).isTrue();
        assertThat(context.get("plan")).contains("custom-alias");
        assertThat(result.artifactJson()).contains("\"replanned\":true");
    }

    private static WorkflowInstance workflow(ScenarioType type, String requirement) {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId("wf-test");
        workflow.setScenarioType(type);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflow.setRequirementText(requirement);
        workflow.setStartedBy("engineer");
        return workflow;
    }

    private static StageNode node(String id, String agent) {
        return new StageNode(id, agent, List.of());
    }
}
