package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DecompositionAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "decomposition";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        List<String> tasks = List.of(
                "Auth and identity",
                "Short-link persistence",
                "Redirect + Redis cache",
                "Click analytics",
                "Feature-flagged brownfield capabilities",
                "Orchestration DAG, gates, rollback"
        );
        String artifact = SimpleJson.object(Map.of("tasks", tasks, "dependencies", "auth -> shortener -> analytics -> orchestration validation"));
        context.put("taskGraph", artifact);
        return AgentResult.ok(artifact, "Converted the normalized spec into sequenced implementation tasks.");
    }
}
