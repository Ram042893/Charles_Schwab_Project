package com.schwab.shortener.orchestration.agent;

import com.schwab.shortener.orchestration.changeset.ChangeSetService;
import com.schwab.shortener.orchestration.changeset.ProposedChangeSet;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import com.schwab.shortener.orchestration.domain.WorkflowStatus;
import com.schwab.shortener.orchestration.engine.StageNode;
import com.schwab.shortener.shortener.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairAndFallbackAgentTest {

    @Mock
    private ChangeSetService changeSets;
    @Mock
    private FeatureFlagService featureFlags;

    @Test
    void repairAgentRegeneratesChangeSetFromLastError() {
        ProposedChangeSet repaired = org.mockito.Mockito.mock(ProposedChangeSet.class);
        when(repaired.getId()).thenReturn("cs-repair");
        when(repaired.getContentHash()).thenReturn("hash-repair");
        when(changeSets.generate(any(), eq(ChangeSetService.Mode.REPAIR), eq("compile failed")))
                .thenReturn(repaired);

        RepairAgent agent = new RepairAgent(changeSets);
        Map<String, String> context = new HashMap<>();
        context.put("lastTestError", "compile failed");

        AgentResult result = agent.execute(workflow(), new StageNode("REPAIR", "repair", List.of()), context);

        assertThat(result.success()).isTrue();
        assertThat(result.artifactJson()).contains("compile failed");
        assertThat(context.get("repairApplied")).isEqualTo("true");
        assertThat(context.get("changeSetId")).isEqualTo("cs-repair");
        verify(changeSets).generate(any(), eq(ChangeSetService.Mode.REPAIR), eq("compile failed"));
    }

    @Test
    void fallbackAgentDisablesFlagsAndUsesReducedMode() {
        ProposedChangeSet reduced = org.mockito.Mockito.mock(ProposedChangeSet.class);
        when(reduced.getId()).thenReturn("cs-reduced");
        when(reduced.getContentHash()).thenReturn("hash-reduced");
        when(changeSets.generate(any(), eq(ChangeSetService.Mode.REDUCED), isNull())).thenReturn(reduced);

        FallbackAgent agent = new FallbackAgent(changeSets, featureFlags);
        Map<String, String> context = new HashMap<>();

        AgentResult result = agent.execute(workflow(), new StageNode("FALLBACK", "fallback", List.of()), context);

        assertThat(result.success()).isTrue();
        assertThat(context.get("fallbackMode")).isEqualTo("true");
        assertThat(context.get("flagsEnabled")).isEqualTo("none");
        verify(featureFlags).setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
        verify(featureFlags).setEnabled(FeatureFlagService.EXPIRATION, false);
        verify(featureFlags).setEnabled(FeatureFlagService.ANALYTICS_EXPORT, false);

        ArgumentCaptor<ChangeSetService.Mode> mode = ArgumentCaptor.forClass(ChangeSetService.Mode.class);
        verify(changeSets).generate(any(), mode.capture(), isNull());
        assertThat(mode.getValue()).isEqualTo(ChangeSetService.Mode.REDUCED);
    }

    private static WorkflowInstance workflow() {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId("wf-1");
        workflow.setScenarioType(ScenarioType.GREENFIELD);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflow.setRequirementText("repair me");
        workflow.setStartedBy("engineer");
        return workflow;
    }
}
