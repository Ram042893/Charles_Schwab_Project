package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RequirementAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "requirement";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String requirement = workflow.getRequirementText();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("intent", "Deliver a reliable URL shortener with analytics, auth, cache, and governed SDLC orchestration");
        spec.put("scenario", workflow.getScenarioType().name());
        spec.put("rawRequirement", requirement);
        spec.put("normalizedCapabilities", List.of("shorten", "redirect", "analytics", "jwt-auth", "redis-cache", "sybase-persistence"));
        if (workflow.getScenarioType() == ScenarioType.BROWNFIELD) {
            spec.put("enhancements", List.of("CUSTOM_ALIAS", "EXPIRATION", "ANALYTICS_EXPORT"));
        }
        context.put("normalizedSpec", SimpleJson.object(spec));
        return AgentResult.ok(SimpleJson.object(spec), "Normalized the incoming requirement into a bounded engineering problem.");
    }
}
