package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentationAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "documentation";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "readme", "README.md",
                "architecture", "docs/ARCHITECTURE.md",
                "scenarios", "docs/SCENARIOS.md",
                "summary", "docs/ENGINEERING_SUMMARY.md"
        ));
        return AgentResult.ok(artifact, "Linked engineering documentation artifacts for review.");
    }
}
