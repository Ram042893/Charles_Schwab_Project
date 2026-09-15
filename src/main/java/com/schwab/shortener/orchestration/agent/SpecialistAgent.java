package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;

import java.util.Map;

public interface SpecialistAgent {
    String name();

    AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context);
}
