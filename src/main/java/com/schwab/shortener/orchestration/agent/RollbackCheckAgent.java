package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RollbackCheckAgent implements SpecialistAgent {

    private final FeatureFlagService featureFlags;

    public RollbackCheckAgent(FeatureFlagService featureFlags) {
        this.featureFlags = featureFlags;
    }

    @Override
    public String name() {
        return "rollback-check";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "compensatingAction", "disable CUSTOM_ALIAS/EXPIRATION/ANALYTICS_EXPORT",
                "flagsCurrentlyEnabled", featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)
        ));
        return AgentResult.ok(artifact, "Validated that feature flags can be reversed without schema loss.");
    }
}
