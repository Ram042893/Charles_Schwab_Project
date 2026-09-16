package com.schwab.shortener.orchestration.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResultTest {

    @Test
    void factoriesSetExpectedFlags() {
        AgentResult ok = AgentResult.ok("{\"a\":1}", "done");
        assertThat(ok.success()).isTrue();
        assertThat(ok.requiresApproval()).isFalse();
        assertThat(ok.rationale()).isEqualTo("done");

        AgentResult approval = AgentResult.approval("{}", "need human", "Approve?");
        assertThat(approval.success()).isTrue();
        assertThat(approval.requiresApproval()).isTrue();
        assertThat(approval.approvalPrompt()).isEqualTo("Approve?");

        AgentResult fail = AgentResult.fail("boom");
        assertThat(fail.success()).isFalse();
        assertThat(fail.rationale()).isEqualTo("boom");
        assertThat(fail.artifactJson()).isEqualTo("{}");
    }
}
