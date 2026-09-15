package com.schwab.shortener.orchestration.engine;

import java.util.List;
import java.util.Set;

public record StageNode(
        String id,
        String agent,
        List<String> dependsOn,
        boolean approvalGate,
        boolean highImpact,
        boolean rollbackable,
        Set<String> entryPolicies
) {
    public StageNode(String id, String agent, List<String> dependsOn) {
        this(id, agent, dependsOn, false, false, false, Set.of());
    }
}
