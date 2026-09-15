package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FallbackAgent implements SpecialistAgent {

    private final ChangeSetService changeSets;
    private final FeatureFlagService featureFlags;

    public FallbackAgent(ChangeSetService changeSets, FeatureFlagService featureFlags) {
        this.changeSets = changeSets;
        this.featureFlags = featureFlags;
    }

    @Override
    public String name() {
        return "fallback";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
        featureFlags.setEnabled(FeatureFlagService.EXPIRATION, false);
        featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, false);
        ProposedChangeSet reduced = changeSets.generate(workflow, ChangeSetService.Mode.REDUCED, null);
        context.put("fallbackMode", "true");
        context.put("fallbackUsed", "true");
        context.put("flagsEnabled", "none");
        context.put("changeSetId", reduced.getId());
        context.put("changeSetHash", reduced.getContentHash());
        context.put("artifactHash.IMPLEMENTATION", reduced.getContentHash());
        String artifact = SimpleJson.object(Map.of(
                "fallback", true,
                "strategy", "reduced-scope-core-shortener",
                "changeSetId", reduced.getId(),
                "contentHash", reduced.getContentHash()
        ));
        return AgentResult.ok(artifact, "Applied reduced-scope fallback after primary retries were exhausted.");
    }
}
