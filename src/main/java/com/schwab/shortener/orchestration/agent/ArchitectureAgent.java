package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ArchitectureAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "architecture";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String artifact = SimpleJson.object(Map.of(
                "style", "modular monolith",
                "persistence", "Sybase ASE via jTDS / Hibernate SybaseASEDialect",
                "cache", "Redis for redirect hot path and feature flags",
                "security", "JWT access/refresh + RBAC",
                "orchestration", "stateful DAG with human gates, retries, rollback"
        ));
        context.put("architecture", artifact);
        return AgentResult.ok(artifact, "Selected a modular monolith so orchestration and product APIs share transactional context.");
    }
}
