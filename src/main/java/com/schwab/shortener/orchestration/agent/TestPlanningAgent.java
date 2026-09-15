package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TestPlanningAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "test-planning";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "cases",
                List.of("login issues JWT", "shorten public URL", "reject private URL", "redirect increments analytics", "feature flags gate brownfield APIs")
        ));
        context.put("testPlan", artifact);
        return AgentResult.ok(artifact, "Prepared a validation plan covering success, security, and feature-flag paths.");
    }
}
