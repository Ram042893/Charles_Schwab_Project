package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
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
    private final ChangeSetService changeSets;

    public ImplementationAgent(
            ApplicationContext applicationContext,
            FeatureFlagService featureFlags,
            ChangeSetService changeSets
    ) {
        this.applicationContext = applicationContext;
        this.featureFlags = featureFlags;
        this.changeSets = changeSets;
    }

    @Override
    public String name() {
        return "implementation";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String requirement = workflow.getRequirementText() == null ? "" : workflow.getRequirementText();
        boolean fallbackMode = "true".equals(context.get("fallbackMode"));
        if (requirement.contains("[inject-fail]") && !fallbackMode) {
            return AgentResult.fail("Injected implementation failure before fallback");
        }

        List<String> verified = new ArrayList<>();
        for (String bean : List.of("urlShortenerService", "authService", "jwtService", "featureFlagService")) {
            applicationContext.getBean(bean);
            verified.add(bean);
        }

        ChangeSetService.Mode mode = fallbackMode
                ? ChangeSetService.Mode.REDUCED
                : "true".equals(context.get("repairApplied")) ? ChangeSetService.Mode.REPAIR : ChangeSetService.Mode.FULL;
        ProposedChangeSet changeSet = changeSets.generate(workflow, mode, context.get("lastTestError"));

        boolean enableAdvanced = !fallbackMode
                && workflow.getScenarioType() != ScenarioType.GREENFIELD
                && mode != ChangeSetService.Mode.REDUCED;
        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, enableAdvanced);
        featureFlags.setEnabled(FeatureFlagService.EXPIRATION, enableAdvanced);
        featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, enableAdvanced);
        context.put("flagsEnabled", enableAdvanced ? "CUSTOM_ALIAS,EXPIRATION,ANALYTICS_EXPORT" : "none");
        context.put("changeSetId", changeSet.getId());
        context.put("changeSetHash", changeSet.getContentHash());
        context.put("artifactHash.IMPLEMENTATION", changeSet.getContentHash());

        String artifact = SimpleJson.object(Map.of(
                "verifiedBeans", verified,
                "scenario", workflow.getScenarioType().name(),
                "changeSetId", changeSet.getId(),
                "contentHash", changeSet.getContentHash(),
                "mode", changeSet.getMode(),
                "files", changeSet.getFilesJson(),
                "unifiedDiffPreview", truncate(changeSet.getUnifiedDiff(), 1200)
        ));
        return AgentResult.ok(artifact, "Generated reviewable source/config diffs and applied scenario runtime flags.");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
