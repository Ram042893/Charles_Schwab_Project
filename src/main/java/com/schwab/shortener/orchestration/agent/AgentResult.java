package com.schwab.shortener.orchestration.agent;

public record AgentResult(boolean success, String artifactJson, String rationale, boolean requiresApproval, String approvalPrompt) {
    public static AgentResult ok(String artifactJson, String rationale) {
        return new AgentResult(true, artifactJson, rationale, false, null);
    }

    public static AgentResult approval(String artifactJson, String rationale, String prompt) {
        return new AgentResult(true, artifactJson, rationale, true, prompt);
    }

    public static AgentResult fail(String message) {
        return new AgentResult(false, "{}", message, false, null);
    }
}
