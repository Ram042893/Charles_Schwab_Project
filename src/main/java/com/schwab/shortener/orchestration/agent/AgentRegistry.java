package com.schwab.shortener.orchestration.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentRegistry {

    private final Map<String, SpecialistAgent> agents;

    public AgentRegistry(List<SpecialistAgent> agents) {
        this.agents = agents.stream().collect(Collectors.toUnmodifiableMap(SpecialistAgent::name, Function.identity()));
    }

    public SpecialistAgent require(String name) {
        SpecialistAgent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalStateException("No specialist agent registered for " + name);
        }
        return agent;
    }
}
