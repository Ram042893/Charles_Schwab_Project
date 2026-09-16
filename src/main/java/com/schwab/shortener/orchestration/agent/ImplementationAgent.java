package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.orchestration.validation.MavenValidationLoopService;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImplementationAgent implements SpecialistAgent {

    private final ApplicationContext applicationContext;
    private final FeatureFlagService featureFlags;
    private final ChangeSetService changeSets;
    private final MavenValidationLoopService mavenValidation;

    public ImplementationAgent(
            ApplicationContext applicationContext,
            FeatureFlagService featureFlags,
            ChangeSetService changeSets,
            MavenValidationLoopService mavenValidation
    ) {
        this.applicationContext = applicationContext;
        this.featureFlags = featureFlags;
        this.changeSets = changeSets;
        this.mavenValidation = mavenValidation;
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

        MavenValidationLoopService.ValidationReport validation = mavenValidation.run(workflow.getId());
        context.put("mavenValidationSuccess", String.valueOf(validation.success()));
        context.put("mavenValidationSkipped", String.valueOf(validation.skipped()));
        if (!validation.skipped() && !validation.success() && !fallbackMode) {
            return AgentResult.fail("Isolated Maven validation failed: " + validation.message());
        }

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("verifiedBeans", verified);
        artifact.put("scenario", workflow.getScenarioType().name());
        artifact.put("changeSetId", changeSet.getId());
        artifact.put("contentHash", changeSet.getContentHash());
        artifact.put("mode", changeSet.getMode());
        artifact.put("files", changeSet.getFilesJson());
        artifact.put("unifiedDiffPreview", truncate(changeSet.getUnifiedDiff(), 1200));
        artifact.put("mavenValidation", Map.of(
                "enabled", validation.enabled(),
                "skipped", validation.skipped(),
                "success", validation.success(),
                "message", validation.message(),
                "sandboxPath", validation.sandboxPath(),
                "attempts", validation.attempts().size(),
                "firstAttemptExitCode", validation.attempts().isEmpty() ? "" : validation.attempts().getFirst().exitCode(),
                "lastAttemptExitCode", validation.attempts().isEmpty() ? "" : validation.attempts().getLast().exitCode(),
                "targetFile", MavenValidationLoopService.TARGET_RELATIVE
        ));
        return AgentResult.ok(SimpleJson.object(artifact),
                "Generated reviewable diffs, applied flags, and completed isolated Maven validate/repair loop.");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
