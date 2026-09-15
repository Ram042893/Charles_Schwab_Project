package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AmbiguityAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "ambiguity";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "ambiguousTerms", List.of("better", "enterprise ready", "faster"),
                "openQuestions", List.of("Is the goal performance, features, or compliance?", "Which SLAs apply?", "Is custom branding required?"),
                "proposedAssumptions", List.of("Enable custom aliases", "Enable expiration", "Enable analytics export")
        ));
        context.put("ambiguity", artifact);
        return AgentResult.ok(artifact, "Detected underspecified intent and prepared assumptions for human confirmation.");
    }
}
