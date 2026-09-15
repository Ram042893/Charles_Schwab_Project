package com.schwab.shortener.orchestration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrchestrationResilienceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greenfieldProducesReviewableChangeSetAndParallelWaveAudit() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/greenfield",
                "Build a URL shortener with auth, analytics, cache and reliability.");
        assertThat((String) JsonPath.read(started, "$.changeSet.mode")).isEqualTo("FULL");
        assertThat((String) JsonPath.read(started, "$.changeSet.unifiedDiff")).contains("WorkflowCapabilityProfile.java");

        List<String> audits = JsonPath.read(started, "$.audit[?(@.eventType=='PARALLEL_WAVE')].details");
        assertThat(audits).anyMatch(details -> details.contains("IMPLEMENTATION") && details.contains("TEST_PLANNING"));

        mockMvc.perform(get("/api/v1/orchestration/workflows/" + JsonPath.read(started, "$.workflow.id") + "/changeset")
                        .header("Authorization", "Bearer " + engineer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").exists())
                .andExpect(jsonPath("$.unifiedDiff").exists());

        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
    }

    @Test
    void implementationFallbackRecoversAfterInjectedFailures() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/brownfield",
                "Add aliases [inject-fail] with expiration and export.");
        assertThat((String) JsonPath.read(started, "$.workflow.currentGate")).isEqualTo("CHANGE_CONTROL_GATE");

        String afterChangeControl = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        List<String> fallbacks = JsonPath.read(afterChangeControl, "$.audit[?(@.eventType=='FALLBACK_SUCCEEDED')].eventType");
        assertThat(fallbacks).isNotEmpty();
        assertThat((String) JsonPath.read(afterChangeControl, "$.changeSet.mode")).isEqualTo("REDUCED");
        assertThat(((Number) JsonPath.read(afterChangeControl, "$.workflow.retryCount")).intValue()).isGreaterThan(0);

        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
    }

    @Test
    void validationRepairRecoversInjectedTestFailure() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/brownfield",
                "Add custom aliases [inject-test-fail] expiration and analytics export.");
        String afterChangeControl = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        List<String> repairs = JsonPath.read(afterChangeControl, "$.audit[?(@.eventType=='VALIDATION_REPAIR')].eventType");
        assertThat(repairs).isNotEmpty();
        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
    }

    @Test
    void selectiveReplanResetsOnlyDownstreamOfChangedStage() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/greenfield", "Build core shortener APIs");
        String workflowId = JsonPath.read(started, "$.workflow.id");
        assertThat(stageStatus(started, "IMPLEMENTATION")).isEqualTo("COMPLETED");
        assertThat(stageStatus(started, "TEST_EXECUTION")).isEqualTo("COMPLETED");

        MvcResult replan = mockMvc.perform(post("/api/v1/orchestration/workflows/" + workflowId + "/replan")
                        .header("Authorization", "Bearer " + engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"architecture artifact refreshed\",\"changedStageId\":\"TEST_PLANNING\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = replan.getResponse().getContentAsString();
        assertThat(stageStatus(body, "IMPLEMENTATION")).isEqualTo("COMPLETED");
        assertThat(stageStatus(body, "TEST_PLANNING")).isIn("COMPLETED", "WAITING_APPROVAL", "PENDING");
        List<String> audit = JsonPath.read(body, "$.audit[?(@.eventType=='REPLAN')].details");
        assertThat(audit).anyMatch(details -> details.contains("TEST_PLANNING") && details.contains("TEST_EXECUTION"));
        assertThat(audit).noneMatch(details -> details.equals("IMPLEMENTATION"));
    }

    @Test
    void safeStopBlocksLaterApproval() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/greenfield", "Build core shortener APIs");
        String workflowId = JsonPath.read(started, "$.workflow.id");

        mockMvc.perform(post("/api/v1/orchestration/workflows/" + workflowId + "/safe-stop")
                        .header("Authorization", "Bearer " + engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"operator halt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflow.status").value("STOPPED"));

        mockMvc.perform(post("/api/v1/orchestration/workflows/" + workflowId + "/approve")
                        .header("Authorization", "Bearer " + reviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"should fail\"}"))
                .andExpect(status().isConflict());
    }

    private String start(String token, String path, String requirement) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"" + requirement + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String approve(String token, String workflowId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orchestration/workflows/" + workflowId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved for resilience demo\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String stageStatus(String body, String stageId) {
        List<?> statuses = JsonPath.read(body, "$.stages[?(@.stageId=='" + stageId + "')].status");
        return statuses.isEmpty() ? "MISSING" : String.valueOf(statuses.getFirst());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
