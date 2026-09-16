package com.schwab.shortener.orchestration.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MavenValidationLoopServiceTest {

    @Test
    void breaksGuardSourceAndRepairsFromFailureOutput() throws Exception {
        String original = new String(
                getClass().getClassLoader()
                        .getResourceAsStream("validation-fixture/src/main/java/com/schwab/shortener/shortener/UrlSafetyGuard.java")
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );
        String broken = MavenValidationLoopService.breakLoopbackGuard(original);
        assertThat(broken).isNotEqualTo(original);
        assertThat(broken).contains("INTENTIONALLY BROKEN");
        assertThat(MavenValidationLoopService.reviseFromFailure(
                original, broken, "rejectsLoopback expected Private rejection for 127.0.0.1")).isEqualTo(original);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void isolatedWorkspaceFailsThenRepairsUntilTestsPass() {
        MavenValidationLoopService service = new MavenValidationLoopService(true, 3, "");
        MavenValidationLoopService.ValidationReport report = service.run("maven-loop-demo");
        assertThat(report.skipped()).as(report.message()).isFalse();
        assertThat(report.success()).as(report.message() + " :: " + summarize(report)).isTrue();
        assertThat(report.attempts()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(report.attempts().getFirst().exitCode()).isNotZero();
        assertThat(report.attempts().getLast().exitCode()).isZero();
        assertThat(report.attempts().getFirst().output().toLowerCase()).containsAnyOf(
                "rejectsloopback", "127.0.0.1", "private", "failures"
        );
    }

    private static String summarize(MavenValidationLoopService.ValidationReport report) {
        if (report.attempts().isEmpty()) {
            return "no attempts";
        }
        var last = report.attempts().getLast();
        return "exit=" + last.exitCode() + " output=" + last.output();
    }
}
