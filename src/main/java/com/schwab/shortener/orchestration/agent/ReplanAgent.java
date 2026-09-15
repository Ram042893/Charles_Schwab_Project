package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReplanAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "replan";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String assumptions = context.getOrDefault("approvedAssumptions", "custom-alias,expiration,analytics-export");
        context.put("plan", "Proceed with brownfield-like feature enablement under approved assumptions: " + assumptions);
        String artifact = SimpleJson.object(Map.of("replanned", true, "assumptions", assumptions));
        return AgentResult.ok(artifact, "Re-planned downstream work after upstream clarification.");
    }
}
