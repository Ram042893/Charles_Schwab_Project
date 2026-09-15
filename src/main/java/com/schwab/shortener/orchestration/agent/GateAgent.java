package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GateAgent implements SpecialistAgent {
    @Override
    public String name() {
        return "gate";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        String prompt = switch (stage.id()) {
            case "CHANGE_CONTROL_GATE" -> "Approve brownfield API/schema/feature-flag change?";
            case "CLARIFICATION_GATE" -> "Accept assumptions: custom aliases, expiration, analytics export?";
            default -> "Approve release of workflow " + workflow.getId() + "?";
        };
        String artifact = SimpleJson.object(Map.of("gate", stage.id(), "highImpact", stage.highImpact()));
        return AgentResult.approval(artifact, "Human approval required before high-impact work continues.", prompt);
    }
}
