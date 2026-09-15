package com.schwab.shortener.orchestration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrchestrationScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greenfieldStopsAtReleaseApprovalThenCompletes() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/greenfield",
                "Build a URL shortener with auth, analytics, cache and reliability.");
        assertThat((String) JsonPath.read(started, "$.workflow.status")).isEqualTo("WAITING_APPROVAL");
        assertThat((String) JsonPath.read(started, "$.workflow.currentGate")).isEqualTo("RELEASE_APPROVAL_GATE");
        assertThat(stageStatus(started, "IMPLEMENTATION")).isEqualTo("COMPLETED");
        assertThat(stageStatus(started, "TEST_PLANNING")).isEqualTo("COMPLETED");
        assertThat(stageStatus(started, "TEST_EXECUTION")).isEqualTo("COMPLETED");

        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
        assertThat(stageStatus(finished, "RELEASE_READINESS")).isEqualTo("COMPLETED");
    }

    @Test
    void brownfieldRequiresChangeControlAndCanComplete() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/brownfield",
                "Add custom aliases, expiration and analytics export to the existing shortener.");
        assertThat((String) JsonPath.read(started, "$.workflow.currentGate")).isEqualTo("CHANGE_CONTROL_GATE");
        assertThat(stageStatus(started, "IMPACT_ANALYSIS")).isEqualTo("COMPLETED");

        String afterChangeControl = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(afterChangeControl, "$.workflow.currentGate")).isEqualTo("RELEASE_APPROVAL_GATE");
        assertThat(stageStatus(afterChangeControl, "IMPLEMENTATION")).isEqualTo("COMPLETED");

        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
        assertThat(((Number) JsonPath.read(finished, "$.workflow.rollbackCount")).intValue()).isZero();
    }

    @Test
    void ambiguousWorkflowClarifiesAssumptionsThenCompletes() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String reviewer = login("reviewer", "Reviewer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/ambiguous",
                "Make the shortener better and more enterprise ready.");
        assertThat((String) JsonPath.read(started, "$.workflow.currentGate")).isEqualTo("CLARIFICATION_GATE");
        assertThat(stageStatus(started, "AMBIGUITY_DETECTION")).isEqualTo("COMPLETED");

        String afterClarification = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat(stageStatus(afterClarification, "DYNAMIC_REPLAN")).isEqualTo("COMPLETED");
        assertThat((String) JsonPath.read(afterClarification, "$.workflow.currentGate")).isEqualTo("RELEASE_APPROVAL_GATE");

        String finished = approve(reviewer, JsonPath.read(started, "$.workflow.id"));
        assertThat((String) JsonPath.read(finished, "$.workflow.status")).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/v1/orchestration/metrics").header("Authorization", "Bearer " + engineer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowsStarted").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void engineerCannotApproveHighImpactGate() throws Exception {
        String engineer = login("engineer", "Engineer@123");
        String started = start(engineer, "/api/v1/orchestration/scenarios/greenfield", "Build core shortener APIs");
        mockMvc.perform(post("/api/v1/orchestration/workflows/" + JsonPath.read(started, "$.workflow.id") + "/approve")
                        .header("Authorization", "Bearer " + engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"self approve\"}"))
                .andExpect(status().isForbidden());
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
                        .content("{\"comment\":\"approved for demo\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String stageStatus(String body, String stageId) {
        java.util.List<?> statuses = JsonPath.read(body, "$.stages[?(@.stageId=='" + stageId + "')].status");
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
