package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ImpactAnalysisAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "impact";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "impactedModules", List.of("UrlShortenerService", "FeatureFlagService", "UrlController", "Redis cache keys"),
                "apiDelta", List.of("customAlias", "expiresAt", "analytics export"),
                "risk", "MEDIUM",
                "requiresChangeControl", true
        ));
        context.put("impact", artifact);
        return AgentResult.ok(artifact, "Mapped brownfield change onto existing modules, APIs, and cache keys.");
    }
}
