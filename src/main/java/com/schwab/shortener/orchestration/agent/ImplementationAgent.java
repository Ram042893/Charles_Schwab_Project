package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ImplementationAgent implements SpecialistAgent {

    private final ApplicationContext applicationContext;
    private final FeatureFlagService featureFlags;

    public ImplementationAgent(ApplicationContext applicationContext, FeatureFlagService featureFlags) {
        this.applicationContext = applicationContext;
        this.featureFlags = featureFlags;
    }

    @Override
    public String name() {
        return "implementation";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        List<String> verified = new ArrayList<>();
        for (String bean : List.of("urlShortenerService", "authService", "jwtService", "featureFlagService")) {
            applicationContext.getBean(bean);
            verified.add(bean);
        }
        if (workflow.getScenarioType() == ScenarioType.GREENFIELD) {
            featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
            featureFlags.setEnabled(FeatureFlagService.EXPIRATION, false);
            featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, false);
            context.put("flagsEnabled", "none");
        } else {
            featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, true);
            featureFlags.setEnabled(FeatureFlagService.EXPIRATION, true);
            featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, true);
            context.put("flagsEnabled", "CUSTOM_ALIAS,EXPIRATION,ANALYTICS_EXPORT");
        }
        String artifact = SimpleJson.object(Map.of("verifiedBeans", verified, "scenario", workflow.getScenarioType().name()));
        return AgentResult.ok(artifact, "Verified live modules and applied scenario-specific runtime changes.");
    }
}
