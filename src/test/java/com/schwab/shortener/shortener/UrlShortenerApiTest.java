package com.schwab.shortener.shortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlShortenerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagService featureFlags;

    private String token;

    @BeforeEach
    void login() throws Exception {
        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, false);
        featureFlags.setEnabled(FeatureFlagService.EXPIRATION, false);
        featureFlags.setEnabled(FeatureFlagService.ANALYTICS_EXPORT, false);
        token = login("engineer", "Engineer@123");
    }

    @Test
    void shortensRedirectsAndTracksAnalytics() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/docs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andReturn();
        String code = JsonPath.read(created.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(get("/s/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"));

        mockMvc.perform(get("/api/v1/urls/" + code + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1));

        mockMvc.perform(delete("/api/v1/urls/" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsPrivateTargetsAndDisabledBrownfieldFeatures() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"http://127.0.0.1/admin\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/x\",\"customAlias\":\"campaign1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void enablesCustomAliasAfterFeatureFlag() throws Exception {
        featureFlags.setEnabled(FeatureFlagService.CUSTOM_ALIAS, true);
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/promo\",\"customAlias\":\"promo-path\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("promo-path"));
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
