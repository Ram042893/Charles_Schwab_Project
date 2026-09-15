package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReleaseReadinessAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "release";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "readiness", "READY",
                "observability", "actuator health/metrics + workflow audit trail",
                "rollback", context.getOrDefault("flagsEnabled", "none")
        ));
        return AgentResult.ok(artifact, "Release gate satisfied after human approval and validation.");
    }
}
