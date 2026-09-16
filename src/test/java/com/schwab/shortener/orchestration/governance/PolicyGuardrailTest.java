package com.schwab.shortener.orchestration.governance;

import com.schwab.shortener.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyGuardrailTest {

    private final PolicyGuardrail guardrail = new PolicyGuardrail();

    @Test
    void allowsCleanRequirements() {
        assertThatCode(() -> guardrail.assertNoSecrets("Build a shortener with analytics"))
                .doesNotThrowAnyException();
        assertThatCode(() -> guardrail.assertNoSecrets(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmbeddedSecrets() {
        assertThatThrownBy(() -> guardrail.assertNoSecrets("db password=SuperSecret1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Policy violation")
                .extracting(ex -> ((BusinessException) ex).status())
                .isEqualTo(422);

        assertThatThrownBy(() -> guardrail.assertNoSecrets("api_key: abc123"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guardrail.assertNoSecrets("token = xyz"))
                .isInstanceOf(BusinessException.class);
    }
}
