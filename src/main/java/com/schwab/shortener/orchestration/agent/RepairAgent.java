package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RepairAgent implements SpecialistAgent {

    private final ChangeSetService changeSets;

    public RepairAgent(ChangeSetService changeSets) {
        this.changeSets = changeSets;
    }

    @Override
    public String name() {
        return "repair";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String error = context.getOrDefault("lastTestError", "unknown validation failure");
        ProposedChangeSet repaired = changeSets.generate(workflow, ChangeSetService.Mode.REPAIR, error);
        context.put("repairApplied", "true");
        context.put("changeSetId", repaired.getId());
        context.put("changeSetHash", repaired.getContentHash());
        context.put("artifactHash.IMPLEMENTATION", repaired.getContentHash());
        String artifact = SimpleJson.object(Map.of(
                "repaired", true,
                "fromError", error,
                "changeSetId", repaired.getId(),
                "contentHash", repaired.getContentHash()
        ));
        return AgentResult.ok(artifact, "Analyzed validation failure and regenerated a corrected change set.");
    }
}
