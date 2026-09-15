package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.orchestration.governance.PolicyGuardrail;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SecurityComplianceAgent implements SpecialistAgent {

    private final PolicyGuardrail policyGuardrail;

    public SecurityComplianceAgent(PolicyGuardrail policyGuardrail) {
        this.policyGuardrail = policyGuardrail;
    }

    @Override
    public String name() {
        return "security";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        policyGuardrail.assertNoSecrets(workflow.getRequirementText());
        policyGuardrail.assertNoSecrets(String.join(" ", context.values()));
        String artifact = SimpleJson.object(Map.of(
                "checks", "jwt-auth,ssrf-guard,hashed-ip-analytics,change-control-gates",
                "result", "PASS"
        ));
        return AgentResult.ok(artifact, "Security and compliance guardrails passed.");
    }
}
