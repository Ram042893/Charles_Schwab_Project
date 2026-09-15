package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.common.BusinessException;
import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.shortener.FeatureFlagService;
import com.schwab.shortener.shortener.UrlSafetyGuard;
import com.schwab.shortener.shortener.UrlShortenerService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TestingAgent implements SpecialistAgent {

    private final UrlShortenerService urls;
    private final FeatureFlagService featureFlags;
    private final UrlSafetyGuard safetyGuard;

    public TestingAgent(UrlShortenerService urls, FeatureFlagService featureFlags, UrlSafetyGuard safetyGuard) {
        this.urls = urls;
        this.featureFlags = featureFlags;
        this.safetyGuard = safetyGuard;
    }

    @Override
    public String name() {
        return "testing";
    }

    @Override
    public AgentResult execute(WorkflowInstance workflow, StageNode stage, Map<String, String> context) {
        List<String> passed = new ArrayList<>();
        UrlShortenerService.UrlView created = urls.create("engineer", new UrlShortenerService.CreateUrlRequest("https://example.com/agentic", null, null));
        urls.resolveActive(created.code());
        passed.add("core-shorten-redirect");
        try {
            safetyGuard.validatePublicHttpUrl("http://127.0.0.1/admin");
            return AgentResult.fail("SSRF guard failed to block loopback URL");
        } catch (BusinessException ex) {
            passed.add("ssrf-guard");
        }
        if (workflow.getScenarioType() != ScenarioType.GREENFIELD) {
            UrlShortenerService.UrlView aliased = urls.create("engineer", new UrlShortenerService.CreateUrlRequest(
                    "https://example.com/alias", "demo-alias-" + System.currentTimeMillis() % 100000, Instant.now().plus(2, ChronoUnit.DAYS)));
            urls.exportAnalytics("engineer", aliased.code());
            passed.add("brownfield-flags");
        } else if (featureFlags.isEnabled(FeatureFlagService.CUSTOM_ALIAS)) {
            return AgentResult.fail("Greenfield must keep advanced flags disabled");
        }
        String artifact = SimpleJson.object(Map.of("passed", passed));
        context.put("testResults", artifact);
        return AgentResult.ok(artifact, "Executed in-process validation against the live shortener.");
    }
}
