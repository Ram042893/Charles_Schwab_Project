package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.orchestration.governance.PolicyGuardrail;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportingAgentsTest {

    @Test
    void architectureDocumentationAndReleaseAgentsSucceed() {
        WorkflowInstance workflow = workflow("clean requirement");
        Map<String, String> context = new HashMap<>();
        context.put("flagsEnabled", "CUSTOM_ALIAS");

        AgentResult architecture = new ArchitectureAgent().execute(workflow, node("ARCHITECTURE_DESIGN", "architecture"), context);
        assertThat(architecture.artifactJson()).contains("modular monolith");
        assertThat(context).containsKey("architecture");

        AgentResult docs = new DocumentationAgent().execute(workflow, node("DOCUMENTATION", "documentation"), context);
        assertThat(docs.artifactJson()).contains("ARCHITECTURE.md");

        AgentResult release = new ReleaseReadinessAgent().execute(workflow, node("RELEASE_READINESS", "release"), context);
        assertThat(release.artifactJson()).contains("READY");
        assertThat(release.artifactJson()).contains("CUSTOM_ALIAS");
    }

    @Test
    void securityAgentPassesCleanContextAndFailsOnSecrets() {
        SecurityComplianceAgent agent = new SecurityComplianceAgent(new PolicyGuardrail());
        Map<String, String> context = new HashMap<>();
        context.put("normalizedSpec", "{\"intent\":\"shortener\"}");

        AgentResult pass = agent.execute(workflow("Build shortener"), node("SECURITY_COMPLIANCE", "security"), context);
        assertThat(pass.success()).isTrue();
        assertThat(pass.artifactJson()).contains("PASS");

        assertThatThrownBy(() -> agent.execute(workflow("password=leak"), node("SECURITY_COMPLIANCE", "security"), context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Policy violation");
    }

    @Test
    void registryResolvesAgentsByName() {
        SpecialistAgent requirement = new RequirementAgent();
        AgentRegistry registry = new AgentRegistry(List.of(requirement));
        assertThat(registry.require("requirement")).isSameAs(requirement);
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    private static WorkflowInstance workflow(String requirement) {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId("wf-support");
        workflow.setScenarioType(ScenarioType.GREENFIELD);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflow.setRequirementText(requirement);
        workflow.setStartedBy("engineer");
        return workflow;
    }

    private static StageNode node(String id, String agent) {
        return new StageNode(id, agent, List.of());
    }
}
